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
package com.collaborne.jsonschema.generator.driver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.Generator;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.collaborne.jsonschema.generator.model.Mappings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfigurationBuilder;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration;
import com.google.common.annotations.VisibleForTesting;

/**
 * Generic driver for a {@link Generator}
 * 
 * This class contains basic functionality to use the provided {@link Generator} to create code.
 * 
 * @author andreas
 */
public class GeneratorDriver {
	private final Logger logger = LoggerFactory.getLogger(GeneratorDriver.class);
	private final ObjectMapper objectMapper;
	private final Generator generator;
	
	public GeneratorDriver(Generator generator) {
		this(JacksonUtils.newMapper(), generator);
	}

	@VisibleForTesting
	protected GeneratorDriver(ObjectMapper objectMapper, Generator generator) {
		this.objectMapper = objectMapper;
		this.generator = generator;
	}

	// TODO: should split this, and provide the Set<URI> for the needed types here
	@Deprecated
	public void run(Path baseDirectory, URI rootUri, List<Path> schemaFiles) throws IOException, CodeGenerationException {
		// Load the schemas
		// XXX: for testing we configure the generator from the outside
		if (schemaFiles.isEmpty()) {
			throw new IllegalStateException("No schema files provided");
		}
		
		SchemaLoader schemas = createSchemaLoader(rootUri, baseDirectory, schemaFiles);
		generator.setSchemaLoader(schemas);
		
		// Now, start the generation by asking for the types implied in the schemas (i.e. with an empty pointer):
		Set<URI> initialTypes = getInitialTypes(rootUri, baseDirectory, schemaFiles);
		generate(initialTypes);
	}

	/**
	 * Generate code for the given {@code types}.
	 *
	 * @param types
	 * @throws CodeGenerationException
	 */
	public void generate(Collection<URI> types) throws CodeGenerationException {
		for (URI type : types) {
			ClassName className = generator.generate(type);
			if (className != null) {
				logger.info("{}: Generated {}.{}", type, className.getPackageName(), className.getRawClassName());
			}
		}
	}
	
	/**
	 * Add mappings from the given path
	 *
	 * @param mappingFile
	 * @throws IOException
	 */
	public void addMappings(Path mappingFile) throws IOException {
		try (InputStream input = Files.newInputStream(mappingFile)) {
			Mappings mappings = objectMapper.readValue(input, Mappings.class);

			addMappings(mappings);
		}
	}
	
	/**
	 * Add mappings
	 *
	 * @param mappings
	 */
	public void addMappings(Mappings mappings) {
		if (mappings.getMappings() != null) {
			for (Mapping mapping : mappings.getMappings()) {
				// Resolve the target against the base URI if given
				// XXX: otherwise we should use the base URI of the mapping file?
				URI target;
				if (mappings.getBaseUri() != null) {
					target = mappings.getBaseUri().resolve(mapping.getTarget());
				} else {
					target = mapping.getTarget();
				}

				// Work out the full class name and update the mapping
				ClassName className = mapping.getClassName();
				if (className == null) {
					throw new IllegalArgumentException("Mapping for " + target + " must specify at least a 'className'");
				}

				generator.addMapping(target, mapping);
			}
		}

		String defaultPackageName = mappings.getDefaultPackageName();
		if (defaultPackageName != null && !defaultPackageName.isEmpty()) {
			URI baseUri = mappings.getBaseUri();
			if (baseUri == null) {
				// XXX: same problem as above, we need to know where the mapping file itself
				//      is to produce a reasonable default.
				logger.warn("Missing baseUri for {}", defaultPackageName);
			} else {
				generator.addDefaultPackageName(baseUri, defaultPackageName);
			}
		}
	}

	/**
	 * Calculate type URIs for all the given {@code schemaFiles}.
	 *
	 * @param rootUri
	 * @param baseDirectory
	 * @param schemaFiles
	 * @return
	 */
	public Set<URI> getInitialTypes(URI rootUri, Path baseDirectory, List<Path> schemaFiles) {
		Set<URI> types = new HashSet<>();
		URI baseDirectoryUri = baseDirectory.toAbsolutePath().normalize().toUri();
		for (Path schemaFile : schemaFiles) {
			URI schemaFileUri = schemaFile.toAbsolutePath().normalize().toUri();
			URI relativeSchemaUri = baseDirectoryUri.relativize(schemaFileUri);
			URI schemaUri = rootUri.resolve(relativeSchemaUri);
			
			types.add(schemaUri.resolve("#"));
		}
		
		return types;
	}

	/**
	 * Create a {@link SchemaLoader} with the provided {@code rootUri} and {@code baseDirectory}.
	 *
	 * @param rootUri
	 * @param baseDirectory
	 * @return
	 * @throws IOException
	 * @see {@link #createSchemaLoader(URI, Path, List)}
	 */
	public SchemaLoader createSchemaLoader(URI rootUri, Path baseDirectory) throws IOException {
		return createSchemaLoader(rootUri, baseDirectory, Collections.emptyList());
	}
	
	/**
	 * Create a {@link SchemaLoader} with the provided {@code rootUri} and {@code baseDirectory}.
	 *
	 * All schemas from {@code schemaFiles} are pre-loaded into the schema loader.
	 *
	 * @param rootUri
	 * @param baseDirectory
	 * @param schemaFiles
	 * @return
	 * @throws IOException
	 */
	public SchemaLoader createSchemaLoader(URI rootUri, Path baseDirectory, List<Path> schemaFiles) throws IOException {
		URI baseDirectoryUri = baseDirectory.toAbsolutePath().normalize().toUri();
		
		// We're not adding a path redirection here, because that changes the path of the loaded schemas to the redirected location.
		// FIXME: This really looks like a bug in the SchemaLoader itself!
		URITranslatorConfiguration uriTranslatorConfiguration = URITranslatorConfiguration.newBuilder()
			.setNamespace(rootUri)
			.freeze();

		LoadingConfigurationBuilder loadingConfigurationBuilder = LoadingConfiguration.newBuilder()
			.setURITranslatorConfiguration(uriTranslatorConfiguration);

		// ... instead, we use a custom downloader which executes the redirect
		Map<String, URIDownloader> downloaders = loadingConfigurationBuilder.freeze().getDownloaderMap();
		URIDownloader redirectingDownloader = new URIDownloader() {
			@Override
			public InputStream fetch(URI source) throws IOException {
				URI relativeSourceUri = rootUri.relativize(source);
				if (!relativeSourceUri.isAbsolute()) {
					// Apply the redirect
					source = baseDirectoryUri.resolve(relativeSourceUri);
				}
				
				URIDownloader wrappedDownloader = downloaders.get(source.getScheme());
				return wrappedDownloader.fetch(source);
			}
		};
		for (Map.Entry<String, URIDownloader> entry : downloaders.entrySet()) {
			loadingConfigurationBuilder.addScheme(entry.getKey(), redirectingDownloader);
		}
		
		JsonNodeReader reader = new JsonNodeReader(objectMapper);
		for (Path schemaFile : schemaFiles) {
			URI schemaFileUri = schemaFile.toAbsolutePath().normalize().toUri();
			URI relativeSchemaUri = baseDirectoryUri.relativize(schemaFileUri);
			URI schemaUri = rootUri.resolve(relativeSchemaUri);

			logger.info("{}: loading from {}", schemaUri, schemaFile);
			JsonNode schemaNode = reader.fromReader(Files.newBufferedReader(schemaFile));
			// FIXME: (upstream?): the preloaded map is accessed via the "real URI", so we need that one here as well
			//        This smells really wrong, after all we want all these to look like they came from rootUri()
			loadingConfigurationBuilder.preloadSchema(schemaFileUri.toASCIIString(), schemaNode);
		}
		
		return new SchemaLoader(loadingConfigurationBuilder.freeze());
	}
}
