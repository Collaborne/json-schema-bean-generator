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
package com.collaborne.jsonschema.generator.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
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
import com.collaborne.jsonschema.generator.pojo.PojoGenerator;
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
import com.google.inject.Guice;
import com.google.inject.Injector;

public class Main {
	private final Logger logger = LoggerFactory.getLogger(Main.class);
	private final List<Path> schemaFiles = new ArrayList<>();
	private final ObjectMapper objectMapper;
	private final Generator generator;
	
	@VisibleForTesting
	protected Main(ObjectMapper objectMapper, Generator generator) {
		this.objectMapper = objectMapper;
		this.generator = generator;
	}
	
	@VisibleForTesting
	protected void run(Path baseDirectory, URI rootUri) throws IOException, CodeGenerationException {
		// Load the schemas
		// XXX: for testing we configure the generator from the outside
		if (schemaFiles.isEmpty()) {
			throw new IllegalStateException("No schema files provided");
		}
		
		SchemaLoader schemas = loadSchemas(rootUri, baseDirectory, schemaFiles);
		generator.setSchemaLoader(schemas);
		
		// Now, start the generation by asking for the types implied in the schemas (i.e. with an empty pointer):
		Set<URI> initialTypes = getInitialTypes(rootUri, baseDirectory, schemaFiles);
		generate(initialTypes);
	}
	
	@VisibleForTesting
	protected void generate(Collection<URI> types) throws CodeGenerationException {
		for (URI type : types) {
			ClassName className = generator.generate(type);
			if (className != null) {
				logger.info("{}: Generated {}.{}", type, className.getPackageName(), className.getRawClassName());
			}
		}
	}
	
	@VisibleForTesting
	protected void addSchema(Path schemaFile) {
		this.schemaFiles.add(schemaFile);
	}
	
	@VisibleForTesting
	protected void addMappings(Path mappingFile) throws IOException {
		try (InputStream input = Files.newInputStream(mappingFile)) {
			Mappings mappings = objectMapper.readValue(input, Mappings.class);

			addMappings(mappings);
		}
	}
	
	@VisibleForTesting
	protected void addMappings(Mappings mappings) {
		for (Mapping mapping : mappings.getMappings()) {
			// Work out the full class name and update the mapping
			ClassName className = mapping.getClassName();
			if (className == null) {
				// TODO: calculate class name from the target
				mapping.setClassName(className);
			}
			
			// Resolve the target against the base URI if given
			// XXX: otherwise we should use the base URI of the mapping file?
			URI target;
			if (mappings.getBaseUri() != null) {
				target = mappings.getBaseUri().resolve(mapping.getTarget());
			} else {
				target = mapping.getTarget();
			}
			generator.addMapping(target, mapping);			
		}
	}
	
	@VisibleForTesting
	protected Set<URI> getInitialTypes(URI rootUri, Path baseDirectory, List<Path> schemaFiles) {
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
	
	@VisibleForTesting
	protected SchemaLoader loadSchemas(URI rootUri, Path baseDirectory, List<Path> schemaFiles) throws IOException {
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
	
	public static void main(String... args) throws URISyntaxException, ClassNotFoundException, IOException, CodeGenerationException {
		List<Path> schemaFiles = new ArrayList<>();
		List<Path> mappingFiles = new ArrayList<>();
		
		Path baseDirectory = Paths.get(".");
		
		URI rootUri = null;
		Path outputDirectory = baseDirectory;
		Class<? extends Generator> generatorClass = PojoGenerator.class;
		for (int i = 0; i < args.length; i++) {
			if ("--help".equals(args[i]) || "-h".equals(args[i])) {
				System.out.println("Usage: Main [-h|--help] [--mapping MAPPING-FILE...] [--root URI] [--generator GENERATOR-CLASS] [--output-directory OUTPUT-DIRECTORY] SCHEMA-FILE...");
				System.exit(0);
			} else if ("--root".equals(args[i])) {
				String root = args[++i];
				if (!root.endsWith("/")) {
					root += "/";
				}
				rootUri = new URI(root);
			} else if ("--base-directory".equals(args[i])) {
				baseDirectory = Paths.get(args[++i]);
			} else if ("--mapping".equals(args[i])) {
				// XXX: is this relative to the base dir?
				mappingFiles.add(baseDirectory.resolve(args[++i]));
			} else if ("--format".equals(args[i])) {
				generatorClass = Class.forName(args[++i]).asSubclass(Generator.class);
			} else if ("--output-directory".equals(args[i])) {
				outputDirectory = Paths.get(args[++i]);
			} else {
				schemaFiles.add(baseDirectory.resolve(args[i]));
			}
		}

		if (schemaFiles.isEmpty()) {
			System.err.println("at least one schema must be provided");
			System.exit(1);
		}
		
		if (rootUri == null) {
			rootUri = baseDirectory.toAbsolutePath().toUri();
		}
		
		if (!rootUri.isAbsolute()) {
			System.err.println("root URI must be absolute");
			System.exit(1);
		}
		
		Injector injector = Guice.createInjector();
		
		Generator generator = injector.getInstance(generatorClass);
		generator.setFeature(PojoGenerator.FEATURE_IGNORE_MISSING_TYPES, Boolean.TRUE);
		generator.setOutputDirectory(outputDirectory);
		
		ObjectMapper objectMapper = JacksonUtils.newMapper();
		
		Main main = new Main(objectMapper, generator);
		for (Path mappingFile : mappingFiles) {
			main.addMappings(mappingFile);
		}
		for (Path schemaFile : schemaFiles) {
			main.addSchema(schemaFile);
		}
		main.run(baseDirectory, rootUri);
		
		System.exit(0);
	}
}
