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

import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.tree.SchemaTree;

/**
 * Generator for a "class" with properties.
 */
class PojoClassGenerator extends AbstractPojoTypeGenerator {
	private interface PropertyVisitor<T extends Exception> {
		void visitProperty(String propertyName, URI type, SchemaTree schema) throws T;
		void visitProperty(String propertyName, URI type) throws T;
	}
	
	private final Logger logger = LoggerFactory.getLogger(PojoClassGenerator.class);
	
	protected <T extends Exception> boolean visitProperties(SchemaTree schema, PojoClassGenerator.PropertyVisitor<T> visitor) throws T {
		JsonNode propertiesNode = schema.getNode().get("properties");
		if (propertiesNode == null || !propertiesNode.isContainerNode()) {
			return false;
		}
		
		for (Iterator<String> fieldIterator = propertiesNode.fieldNames(); fieldIterator.hasNext(); ) {
			String fieldName = fieldIterator.next();

			SchemaTree propertySchema = schema.append(JsonPointer.of("properties", fieldName));
			PojoClassGenerator.SchemaVisitor<T> schemaVisitor = new PojoClassGenerator.SchemaVisitor<T>() {
				@Override
				public void visitSchema(URI type, SchemaTree schema) throws T {
					visitor.visitProperty(fieldName, type, schema);
				}
				
				@Override
				public void visitSchema(URI type) throws T {
					visitor.visitProperty(fieldName, type);
				}
			};
			
			// XXX: what about "relative" references, won't adding a '#' break resolving those? Are those legal?
			URI elementUri = propertySchema.getLoadingRef().toURI().resolve("#" + propertySchema.getPointer().toString());
			if (!visitSchema(elementUri, propertySchema, schemaVisitor)) {
				// XXX: can there be meta information here?
				// XXX: context information missing here
				logger.warn("{}: not a container value");
			}
		}
		
		return true;
	}
	
	protected Set<URI> getRequiredTypes(SchemaTree schema) {
		Set<URI> requiredTypes = new HashSet<>();
		
		SchemaVisitor<RuntimeException> schemaVisitor = new SchemaVisitor<RuntimeException>() {
			@Override
			public void visitSchema(URI type, SchemaTree schema) {
				visitSchema(type);
			}
			
			@Override
			public void visitSchema(URI type) {
				requiredTypes.add(type);
			}
		}; 
		
		visitProperties(schema, new PropertyVisitor<RuntimeException>() {
			@Override
			public void visitProperty(String propertyName, URI type, SchemaTree schema) {
				visitProperty(propertyName, type);
			}
			
			@Override
			public void visitProperty(String propertyName, URI type) {
				schemaVisitor.visitSchema(type);
			}
		});
		
		// Check if "additionalProperties" is a schema as well, if so we need to be able to resolve that one.
		JsonNode additionalPropertiesNode = schema.getNode().path("additionalProperties");
		if (additionalPropertiesNode.isContainerNode()) {
			SchemaTree additionalPropertiesSchema = schema.append(JsonPointer.of("additionalProperties"));
			URI additionalPropertiesUri = additionalPropertiesSchema.getLoadingRef().toURI().resolve("#" + additionalPropertiesSchema.getPointer().toString());
			visitSchema(additionalPropertiesUri, additionalPropertiesSchema, schemaVisitor);
		}
		return requiredTypes;
	}
	
	@Override
	public void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		Mapping mapping = context.getMapping();
		
		// Process the properties into PropertyGenerators
		List<PojoPropertyGenerator> propertyGenerators = new ArrayList<>();

		visitProperties(schema, new PropertyVisitor<CodeGenerationException>() {
			@Override
			public void visitProperty(String propertyName, URI type, SchemaTree schema) throws CodeGenerationException {
				String defaultValue;
				JsonNode defaultValueNode = schema.getNode().path("default");
				if (defaultValueNode.isMissingNode() || defaultValueNode.isNull()) {
					defaultValue = null;
				} else if (defaultValueNode.isTextual()) {
					defaultValue = '"' + defaultValueNode.textValue() + '"';
				} else {
					defaultValue = defaultValueNode.asText();
				}

				PojoPropertyGenerator propertyGenerator = context.createPropertyGenerator(type, propertyName, defaultValue);
				propertyGenerators.add(propertyGenerator);
			}

			@Override
			public void visitProperty(String propertyName, URI type) throws CodeGenerationException {
				propertyGenerators.add(context.createPropertyGenerator(type, propertyName, null));
			}
		});
		
		for (PojoPropertyGenerator propertyGenerator : propertyGenerators) {
			propertyGenerator.generateImports(writer);
		}

		// check whether we need to work with additionalProperties:
		// - schema can say "yes", or "yes-with-this-type"
		// - mapping can say "yes", or "ignore"
		// Ultimately if we have to handle them we let the generated class extend AbstractMap<String, TYPE>,
		// where TYPE is either the one from the schema, or Object (if the schema didn't specify anything.
		// XXX: "Object" should probably be "whatever our factory/reader would produce"
		// XXX: Instead an AbstractMap, should we have a standard class in our support library?
		ClassName additionalPropertiesValueClassName = null;
		ClassName extendedClass = mapping.getExtends();
		JsonNode additionalPropertiesNode = schema.getNode().path("additionalProperties");
		if (!additionalPropertiesNode.isMissingNode() && !additionalPropertiesNode.isNull() && !mapping.isIgnoreAdditionalProperties()) {
			if (additionalPropertiesNode.isBoolean()) {
				if (additionalPropertiesNode.booleanValue()) {
					additionalPropertiesValueClassName = ClassName.create(Object.class);
				}
			} else {
				assert additionalPropertiesNode.isContainerNode();

				AtomicReference<ClassName> ref = new AtomicReference<>();
				SchemaTree additionalPropertiesSchema = schema.append(JsonPointer.of("additionalProperties"));
				URI additionalPropertiesUri = additionalPropertiesSchema.getLoadingRef().toURI().resolve("#" + additionalPropertiesSchema.getPointer().toString());
				visitSchema(additionalPropertiesUri, additionalPropertiesSchema, new SchemaVisitor<CodeGenerationException>() {
					@Override
					public void visitSchema(URI type, SchemaTree schema) throws CodeGenerationException {
						visitSchema(type);
					}

					@Override
					public void visitSchema(URI type) throws CodeGenerationException {
						ref.set(context.getGenerator().generate(type));
					}
				});
				additionalPropertiesValueClassName = ref.get();
			}

			if (additionalPropertiesValueClassName != null) {
				if (extendedClass != null) {
					// FIXME: handle this by using an interface instead
					throw new CodeGenerationException(context.getType(), "additionalProperties is incompatible with 'extends'");
				}
				extendedClass = ClassName.create(AbstractMap.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				writer.writeImport(extendedClass);
				ClassName mapClass = ClassName.create(Map.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				writer.writeImport(mapClass);
				ClassName hashMapClass = ClassName.create(HashMap.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				writer.writeImport(hashMapClass);
				ClassName mapEntryClass = ClassName.create(Map.Entry.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				writer.writeImport(mapEntryClass);
				ClassName setMapEntryClass = ClassName.create(Set.class, mapEntryClass);
				writer.writeImport(setMapEntryClass);
			}
		}

		if (mapping.getImplements() != null) {
			for (ClassName implementedInterface : mapping.getImplements()) {
				writer.writeImport(implementedInterface);
			}
		}

		writeSchemaDocumentation(schema, writer);
		writer.writeClassStart(mapping.getGeneratedClassName(), extendedClass, mapping.getImplements(), Kind.CLASS, Visibility.PUBLIC, mapping.getModifiers());
		try {
			// Write properties
			for (PojoPropertyGenerator propertyGenerator : propertyGenerators) {
				propertyGenerator.generateFields(writer);
			}
			if (additionalPropertiesValueClassName != null) {
				ClassName mapClass = ClassName.create(Map.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				ClassName hashMapClass = ClassName.create(HashMap.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				writer.writeField(Visibility.PRIVATE, mapClass, "additionalPropertiesMap", () -> {
					writer.write(" = new ");
					// XXX: If code generation would know about java 7/8, we could use diamond here
					writer.writeClassName(hashMapClass);
					writer.write("()");
				});
			}

			// Write accessors
			// TODO: style to create them: pairs, or ordered?
			// TODO: whether to generate setters in the first place, or just getters
			for (PojoPropertyGenerator propertyGenerator : propertyGenerators) {
				propertyGenerator.generateGetter(writer);
				propertyGenerator.generateSetter(writer);
			}

			if (additionalPropertiesValueClassName != null) {
				ClassName mapEntryClass = ClassName.create(Map.Entry.class, ClassName.create(String.class), additionalPropertiesValueClassName);
				ClassName setMapEntryClass = ClassName.create(Set.class, mapEntryClass);
				writer.writeAnnotation(ClassName.create(Override.class));
				writer.writeMethodBodyStart(Visibility.PUBLIC, setMapEntryClass, "entrySet");
				writer.writeCode("return additionalPropertiesMap.entrySet();");
				writer.writeMethodBodyEnd();
			}
		} finally {
			writer.writeClassEnd();
		}
	}
}