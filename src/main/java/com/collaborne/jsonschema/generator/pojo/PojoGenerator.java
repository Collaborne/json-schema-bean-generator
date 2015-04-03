/**
 * Copyright (C) 2015 Collaborne B.V. (opensource@collaborne.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.collaborne.jsonschema.generator.pojo;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.AbstractGenerator;
import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.MissingSchemaException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.annotations.VisibleForTesting;

// XXX: URI vs JsonRef vs SchemaKey
public class PojoGenerator extends AbstractGenerator {
	/** Attempt to write code even when there are some types missing */
	public static final Feature<Boolean> FEATURE_IGNORE_MISSING_TYPES = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/ignore-missing-types", Boolean.class, Boolean.FALSE);

	public enum AnonymousClassNameGenerator {
		CAMEL_CASE {
			@Override
			public String createClassName(URI type) {
				StringBuilder classNameBuilder = new StringBuilder();
				if (type.getFragment().isEmpty()) {
					classNameBuilder.append("Type");
				} else {
					// FIXME: This should be a different generator?
					String fragment = type.getFragment().replace(':', '/');
					for (String fragmentStep : fragment.split("/")) {
						// Skip some typical steps to make the names a bit easier to read
						// - "": happens due to JSON pointer always starting with '/'
						// - "properties": any reference to an anonymous type inside an "object"
						// - "definitions": suggested node for keeping type definitions
						if (fragmentStep.isEmpty() || "properties".equals(fragmentStep) || "definitions".equals(fragmentStep)) {
							continue;
						}
						
						// Replace all characters not valid for Java
						char fragmentStepFirstCharacter = Character.toUpperCase(fragmentStep.charAt(0));
						String fragmentStepRemainingCharacters;
						if (Character.isJavaIdentifierStart(fragmentStepFirstCharacter)) {
							fragmentStepRemainingCharacters = fragmentStep.substring(1);
						} else {
							fragmentStepFirstCharacter = '_';
							fragmentStepRemainingCharacters = fragmentStep;
						}
						fragmentStepRemainingCharacters = fragmentStepRemainingCharacters.replaceAll("[^\\p{javaJavaIdentifierPart}]", "");
						
						classNameBuilder.append(fragmentStepFirstCharacter);
						classNameBuilder.append(fragmentStepRemainingCharacters);
					}
				}

				return classNameBuilder.toString();
			}
		}, 
		DOLLAR {
			@Override
			public String createClassName(URI type) {
				throw new UnsupportedOperationException("AnonymousClassNameGenerator#appendClassName() is not implemented");
			}
		};
		
		public abstract String createClassName(URI type);
	}
	public static final Feature<AnonymousClassNameGenerator> FEATURE_CLASS_NAME_GENERATOR = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/class-name-generator", AnonymousClassNameGenerator.class, AnonymousClassNameGenerator.CAMEL_CASE);
	/** Whether to ignore constraints (enum-ness, min/max value, etc) on non-"object" types */
	public static final Feature<Boolean> FEATURE_USE_SIMPLE_PLAIN_TYPES = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/simple-plain-types", Boolean.class, Boolean.FALSE);
	/** Whether to generate Java 5 {@code enum}s or 'class-with-constants' for JSON schema 'enum's */
	public static final Feature<Kind> FEATURE_ENUM_STYLE = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/enum-style", Kind.class, Kind.ENUM);

	/** Sentinel to detect a recursive generation early */
	private static final ClassName IN_PROGRESS = new ClassName("internal", "IN_PROGRESS");
	
	private static class SimplePojoTypeGenerator implements PojoTypeGenerator {
		private final ClassName className;
		
		public SimplePojoTypeGenerator(ClassName className) {
			this.className = className;
		}
		
		@Override
		public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter javaWriter) {
			return className;
		}
	}
	
	@VisibleForTesting
	protected static class Buffer extends ByteArrayOutputStream {
		public InputStream getInputStream() {
			return new ByteArrayInputStream(buf, 0, count);
		}
	}
	
	private static final List<String> PRIMITIVE_TYPE_NAMES = Arrays.asList(
		Boolean.TYPE.getName(),
		Character.TYPE.getName(),
		Byte.TYPE.getName(),
		Short.TYPE.getName(),
		Integer.TYPE.getName(),
		Long.TYPE.getName(),
		Float.TYPE.getName(),
		Double.TYPE.getName()
	);

	private final Logger logger = LoggerFactory.getLogger(PojoGenerator.class);
	
	private final Map<String, PojoTypeGenerator> typeGenerators = new HashMap<>();
	private final Map<URI, ClassName> generatedClassNames = new HashMap<>();
	private final Set<URI> nullTypes = new HashSet<>();
	/** Stack of calls to {@link #generateInternal(URI, Mapping)}, used for logging */
	private final Stack<URI> generationStack = new Stack<>();
	
	@Inject
	@VisibleForTesting
	protected PojoGenerator(PojoClassGenerator classGenerator, PojoArrayGenerator arrayGenerator, PojoStringGenerator stringGenerator) {
		this.typeGenerators.put("object", classGenerator);
		this.typeGenerators.put("array", arrayGenerator);
		if (getFeature(FEATURE_USE_SIMPLE_PLAIN_TYPES)) {
			// TODO: if additional restrictions are given on these types we can either implement specific
			//       types (for example we provide a base library for each of the plain types, and configure them
			//       to check the restrictions), or we could simply ignore those.
			this.typeGenerators.put("string", new SimplePojoTypeGenerator(ClassName.create(String.class)));
		} else {
			this.typeGenerators.put("string", stringGenerator);
		}
		this.typeGenerators.put("integer", new SimplePojoTypeGenerator(ClassName.create(Integer.TYPE)));
		this.typeGenerators.put("number", new SimplePojoTypeGenerator(ClassName.create(Double.TYPE))); 
		this.typeGenerators.put("boolean", new SimplePojoTypeGenerator(ClassName.create(Boolean.TYPE)));
	}
	
	@Override
	public ClassName generate(URI wantedType) throws CodeGenerationException {
		// Work out the schema and mapping for the given type. There is one complexity involved
		// here: the type might be an
		// "alias", i.e. the schema only contains a "$ref" to another place. In this case we want to follow
		// the ref, until we find a mapping or an actual schema.
		// At any point in this search we may find that we already processed this type (either generated a class,
		// or could use an existing one.)

		// Find or create the mapping for this type
		URI type = wantedType;
		Mapping mapping;
		SchemaTree schema;
		do {
			// Check if we have processed this type
			if (nullTypes.contains(type)) {
				return null;
			}
			ClassName generatedClassName = generatedClassNames.get(type);
			if (generatedClassName != null) {
				if (IN_PROGRESS.equals(generatedClassName)) {
					// XXX: The stack here doesn't contain the intermediate steps
					throw new CodeGenerationException(type, "Recursion detection while generating code for " + wantedType + ": " + generationStack);
				}
				return generatedClassName;
			}

			mapping = getMapping(type);
			// Look up the schema, it should exist.
			try {
				schema = getSchema(getSchemaLoader(), type);
				if (schema == null || schema.getNode() == null) {
					throw new MissingSchemaException(type);
				}
			} catch (ProcessingException e) {
				throw new MissingSchemaException(type, e);
			}

			if (mapping == null) {
				if (schema.getNode().hasNonNull("$ref")) {
					// Schema is actually a $ref, follow it
					String ref = schema.getNode().get("$ref").textValue();
					logger.debug("{}: Following $ref to {}", type, ref);
					// This URI can be relative to the current schema, so we need to properly
					// resolve it here.
					// FIXME: same loading ref problem as everywhere else!
					type = schema.getLoadingRef().toURI().resolve(ref);
				} else {
					// Schema is not a $ref, and we do not have a specific mapping
					// for it.
					// Generate one.
					logger.debug("{}: Defining new mapping", type);
					mapping = generateMapping(type);
					addMapping(type, mapping);
				}
			}
		} while (mapping == null);

		ClassName result;
		// Mark the type as "in progress", as we're now going to actually work with it.
		generationStack.push(type);
		try {
			generatedClassNames.put(type, IN_PROGRESS);
			try {
				result = generateInternal(type, schema, mapping);
			} catch (CodeGenerationException e) {
				logger.error("{}: Exception while generating, source: {}", type, generationStack);
				if (getFeature(FEATURE_IGNORE_MISSING_TYPES)) {
					// Assume that a class would have been created.
					result = mapping.getClassName();
					logger.warn("{}: Ignoring creation failure, assuming class {} would have been created", type, result, e);
				} else {
					throw e;
				}
			}

			if (result == null) {
				nullTypes.add(type);
				generatedClassNames.remove(type);
			} else {
				generatedClassNames.put(type, result);
			}
			return result;
		} finally {
			generationStack.pop();
		}
	}
	
	/**
	 * Generate code for the {@code type} using the provided {@code mapping}.
	 * 
	 * @param type
	 * @param schema
	 * @param mapping
	 * @return the name of the class to use for this type, or {@code null} if no class is available for this type
	 * @throws CodeGenerationException
	 */
	@VisibleForTesting
	protected ClassName generateInternal(URI type, SchemaTree schema, Mapping mapping) throws CodeGenerationException {
		// 1. If the mapping wants a primitive type or existing type, do that (ignoring whatever the schema does)
		ClassName wantedGeneratedClassName = mapping.getGeneratedClassName();
		if (isPrimitive(wantedGeneratedClassName) || isExistingClass(wantedGeneratedClassName)) {
			logger.debug("{}: Mapping to existing class/type {}", type, wantedGeneratedClassName);
			return mapping.getClassName();
		}

		try {
			// 2. Determine the type of the schema
			String schemaType = getSchemaType(type, schema);
			if ("null".equals(schemaType)) {
				// All good, nothing to be done.
				return null;
			}
			
			// 3. Find the correct generator and run the code generation, this might recurse back into the generator
			PojoTypeGenerator typeGenerator = typeGenerators.get(schemaType);
			if (typeGenerator == null) {
				throw new CodeGenerationException(type, "Cannot handle type '" + type + "' ('" + schemaType + "')");
			}
			
			PojoCodeGenerationContext codeGenerationContext = new PojoCodeGenerationContext(this, mapping);
			
			// Generate into a buffer
			// If the generator doesn't actually produce output (for example because it resolved the class differently),
			// then we do not have to do anything further.
			ClassName className;
			try (Buffer buffer = new Buffer()) {
				try (JavaWriter writer = new JavaWriter(new BufferedWriter(new OutputStreamWriter(buffer)))) {
					className = typeGenerator.generate(codeGenerationContext, schema, writer);
				}

				if (buffer.size() > 0) {
					writeSource(type, className, buffer);
				}
			}
			
			return className;
		} catch (IOException e) {
			throw new CodeGenerationException(type, e);
		}
	}

	@VisibleForTesting
	protected String getSchemaType(URI type, SchemaTree schema) throws CodeGenerationException {
		String schemaType;
		JsonNode schemaTypeNode = schema.getNode().get("type");
		if (schemaTypeNode == null) {
			// check whether it is a oneOf/anyOf/allOf
			Set<String> aggregationTypes = new HashSet<>();
			if (schema.getNode().hasNonNull("allOf")) {
				aggregationTypes.add("allOf");
			}
			if (schema.getNode().hasNonNull("anyOf")) {
				aggregationTypes.add("anyOf");
			}
			if (schema.getNode().hasNonNull("oneOf")) {
				aggregationTypes.add("oneOf");
			}

			if (aggregationTypes.size() > 1) {
				throw new CodeGenerationException(type, "Cannot combine multiple aggregation types, found " + aggregationTypes);
			} else if (aggregationTypes.size() == 1) {
				schemaType = aggregationTypes.iterator().next();
			} else {
				// XXX: is assuming "object" ok here, or should this be fatal?
				logger.warn("{}: Missing type keyword, assuming 'object'", type);
				schemaType = "object";
			}
		} else {
			schemaType = schemaTypeNode.textValue();
		}
		return schemaType;
	}

	@VisibleForTesting
	protected boolean isPrimitive(ClassName className) {
		if (!className.getPackageName().isEmpty()) {
			return false;
		}
		return PRIMITIVE_TYPE_NAMES.contains(className.getRawClassName());
	}

	@VisibleForTesting
	protected boolean isExistingClass(ClassName className) {
		String fqcn = className.getPackageName();
		if (!fqcn.isEmpty()) {
			fqcn += ".";
		}
		fqcn += className.getRawClassName();
		// We're using the canonical name, which cannot be used with #forName() with
		// inner classes ('.' would have to be replaced by '$'). So this one tries with the
		// given name, and then replaces each '.' from the end to see if this ClassName
		// refers to an inner class.
		// Note that if this ClassName was created from an actual java.lang.Class we could trust
		// the package information, and skip replacing in that, but that would fail for
		// ClassNames created using #parse().
		int end = fqcn.length();
		do {
			try {
				Class.forName(fqcn);
				return true;
			} catch (ClassNotFoundException e) {
				// Try replacing the next dot
				int next = fqcn.lastIndexOf('.', end);
				if (next != -1) {
					fqcn = fqcn.substring(0, next) + '$' + fqcn.substring(next + 1);
				}
				end = next;
			}
		} while (end != -1);
		return false;
	}

	@VisibleForTesting
	protected void writeSource(URI type, ClassName className, Buffer buffer) throws IOException {
		// Create the file based on the className in the mapping
		Path outputFile = getClassSourceFile(className);
		logger.info("{}: Writing {}", type, outputFile);

		// Write stuff into it
		Files.createDirectories(outputFile.getParent());
		Files.copy(buffer.getInputStream(), outputFile, StandardCopyOption.REPLACE_EXISTING);
	}
	
	protected Path getClassSourceFile(ClassName className) {
		StringBuilder fqcnBuilder = new StringBuilder();
		if (!className.getPackageName().isEmpty()) {
			fqcnBuilder.append(className.getPackageName());
			fqcnBuilder.append(".");
		}
		fqcnBuilder.append(className.getRawClassName());
		String classFileName = fqcnBuilder.toString().replace('.', '/') + ".java";
		return getOutputDirectory().resolve(classFileName);
	}
	
	@VisibleForTesting
	protected Mapping generateMapping(URI type) {
		Mapping mapping = new Mapping();
		mapping.setTarget(type);
		
		// In theory the type could point to a schema that has an 'id' value, which is
		// fairly easy to abuse. So, we generate a name based on the position in the 
		// tree, if the user doesn't like it they can just provide a specific mapping for it.
		String packageName = getDefaultPackageName(type);
		// One cannot "import" packages from the default package, so we need them to be somewhere ... 
		assert packageName != null && !packageName.isEmpty();
		
		// TODO: should produce ClassName directly
		AnonymousClassNameGenerator classNameGenerator = getFeature(FEATURE_CLASS_NAME_GENERATOR);
		String rawClassName = classNameGenerator.createClassName(type);
		ClassName className = new ClassName(packageName, rawClassName);
		while (generatedClassNames.containsValue(className)) {
			// Make the name reasonably unique by adding a timestamp
			className = new ClassName(packageName, rawClassName + "$" + System.nanoTime());
		}
		mapping.setClassName(className);
		return mapping;
	}

	/**
	 * Get the {@link SchemaTree} for the given {@code uri}.
	 * 
	 * This is similar to {@link SchemaLoader#get(URI)}, but allows {@code uri} to contain a fragment.
	 * 
	 * @param uri
	 * @return
	 * @throws ProcessingException 
	 */
	// XXX: Should this be the default behavior of SchemaLoader#get()?
	@VisibleForTesting
	protected SchemaTree getSchema(SchemaLoader schemaLoader, URI uri) throws ProcessingException {
		String fragment = uri.getFragment();
		if (fragment == null) {
			return schemaLoader.get(uri);
		} else {
			try {
				URI schemaTreeUri = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
				JsonPointer pointer = new JsonPointer(fragment);
				SchemaTree schema = schemaLoader.get(schemaTreeUri);
				return schema.setPointer(pointer);
			} catch (URISyntaxException|JsonPointerException e) {
				assert false : "Was a valid before, we split things up!";
				throw new RuntimeException(e);
			}
		}
	}
}
