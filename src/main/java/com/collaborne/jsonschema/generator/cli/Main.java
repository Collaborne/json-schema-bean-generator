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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.Generator;
import com.collaborne.jsonschema.generator.driver.GeneratorDriver;
import com.collaborne.jsonschema.generator.pojo.PojoGenerator;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class Main {
	public static void main(String... args) throws URISyntaxException, ClassNotFoundException, IOException, CodeGenerationException {
		List<Path> schemaFiles = new ArrayList<>();
		List<Path> mappingFiles = new ArrayList<>();
		Set<URI> types = new HashSet<>();
		
		Path baseDirectory = Paths.get(".");
		
		URI rootUri = null;
		Path outputDirectory = baseDirectory;
		Class<? extends Generator> generatorClass = PojoGenerator.class;
		for (int i = 0; i < args.length; i++) {
			if ("--help".equals(args[i]) || "-h".equals(args[i])) {
				System.out.println("Usage: Main [-h|--help] [--mapping MAPPING-FILE...] [--root URI] [--generator GENERATOR-CLASS] [--output-directory OUTPUT-DIRECTORY] [--type URI...] SCHEMA-FILE...");
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
			} else if ("--type".equals(args[i])) {
				types.add(new URI(args[++i]));
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
		
		GeneratorDriver driver = new GeneratorDriver(generator);
		for (Path mappingFile : mappingFiles) {
			driver.addMappings(mappingFile);
		}

		SchemaLoader schemas = driver.createSchemaLoader(rootUri, baseDirectory, schemaFiles);
		generator.setSchemaLoader(schemas);

		// Add all implicit types (i.e. one for each schema file with an empty pointer):
		types.addAll(driver.getInitialTypes(rootUri, baseDirectory, schemaFiles));

		driver.generate(types);

		System.exit(0);
	}
}
