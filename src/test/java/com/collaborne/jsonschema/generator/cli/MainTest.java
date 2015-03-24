/* Licensed to Collaborne B.V. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Collaborne licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.collaborne.jsonschema.generator.cli;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.Generator;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.collaborne.jsonschema.generator.pojo.PojoGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfigurationBuilder;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.inject.Guice;
import com.google.inject.Injector;

// TODO: should always validate the schema first, to avoid bugs caused by invalid schemas
public class MainTest {
	private FileSystem fs;
	private Main main;
	private Generator generator;
	
	@Before
	public void setUp() {
		fs = Jimfs.newFileSystem(Configuration.unix());
		
		Injector injector = Guice.createInjector();
		generator = injector.getInstance(PojoGenerator.class);
		main = new Main(new ObjectMapper(), generator);
	}
	
	@After
	public void tearDown() throws IOException {
		// Dump the contents of the file system
		Path dumpTarget = null;
		Path dumpStart = fs.getPath("/");
		Files.walkFileTree(dumpStart, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				System.out.println("> " + file);
				System.out.println(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				
				// Copy the file if wanted
				if (dumpTarget != null) {
					Path target = dumpTarget.resolve(dumpStart.relativize(file).toString());
					Files.createDirectories(target.getParent());
					Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	private SchemaLoader loadSchema(URI rootUri, String path) throws IOException, ProcessingException {
		LoadingConfigurationBuilder loadingConfigurationBuilder = LoadingConfiguration.newBuilder();
		
		JsonNode schemaNode = new JsonNodeReader().fromInputStream(getClass().getResourceAsStream(path));
		loadingConfigurationBuilder.preloadSchema(rootUri.resolve(path).toASCIIString(), schemaNode);
		
		return new SchemaLoader(loadingConfigurationBuilder.freeze());
	}
	
	@Test
	public void runSmokeTest() throws IOException, ProcessingException, URISyntaxException, JsonPointerException, CodeGenerationException {
		URI rootUri = URI.create("http://example.com/");
		
		Path outputDirectory = fs.getPath("output");
		generator.setOutputDirectory(outputDirectory);
		
		SchemaLoader schemas = loadSchema(rootUri, "/schemas/simple.json");
		generator.setSchemaLoader(schemas);
		
		Mapping rootMapping = new Mapping(URI.create("http://example.com/schemas/simple.json#/definitions/type"), new ClassName("com.example.test.schemas", "Type"));
		generator.addMapping(rootMapping.getTarget(), rootMapping);
		
		main.generate(Collections.singleton(rootMapping.getTarget()));
		
		Path generatedTypeFile = outputDirectory.resolve("com/example/test/schemas/Type.java"); 
		assertTrue(Files.exists(generatedTypeFile));
	}
	
	@Test
	public void runSmokeTestInline() throws IOException, ProcessingException, URISyntaxException, JsonPointerException, CodeGenerationException {
		// XXX: only really checks #resolve()
		URI rootUri = URI.create("http://example.com/");
		
		Path outputDirectory = fs.getPath("output");
		generator.setOutputDirectory(outputDirectory);
		
		SchemaLoader schemas = loadSchema(rootUri, "/schemas/inline.json");
		generator.setSchemaLoader(schemas);

		Mapping rootMapping = new Mapping(URI.create("http://example.com/schemas/inline.json#"), new ClassName("com.example.test.schemas", "WithInline"));
		generator.addMapping(rootMapping.getTarget(), rootMapping);
				
		main.generate(Collections.singleton(rootMapping.getTarget()));
		
		Path generatedTypeFile = outputDirectory.resolve("com/example/test/schemas/WithInline.java"); 
		assertTrue(Files.exists(generatedTypeFile));
	}
	
	@Test
	public void runSmokeTestInlineNested() throws IOException, ProcessingException, URISyntaxException, JsonPointerException, CodeGenerationException {
		// XXX: only really checks #resolve()
		URI rootUri = URI.create("http://example.com/");
		
		Path outputDirectory = fs.getPath("output");
		generator.setOutputDirectory(outputDirectory);

		SchemaLoader schemas = loadSchema(rootUri, "/schemas/nested-inline.json");
		generator.setSchemaLoader(schemas);
		
		Mapping rootMapping = new Mapping(URI.create("http://example.com/schemas/nested-inline.json#"), new ClassName("com.example.test.schemas", "WithInline"));
		generator.addMapping(rootMapping.getTarget(), rootMapping);
		
		main.generate(Collections.singleton(rootMapping.getTarget()));
		
		Path generatedTypeFile = outputDirectory.resolve("com/example/test/schemas/WithInline.java"); 
		assertTrue(Files.exists(generatedTypeFile));
	}
	
	@Test
	public void runSmokeTestInlineExplicitMapping() throws IOException, ProcessingException, URISyntaxException, JsonPointerException, CodeGenerationException {
		// XXX: only really checks #resolve()
		URI rootUri = URI.create("http://example.com/");
		
		Path outputDirectory = fs.getPath("output");
		generator.setOutputDirectory(outputDirectory);
		
		SchemaLoader schemas = loadSchema(rootUri, "/schemas/inline.json");
		generator.setSchemaLoader(schemas);
		
		Mapping rootMapping = new Mapping(URI.create("http://example.com/schemas/inline.json#"), new ClassName("com.example.test.schemas", "WithInline"));
		generator.addMapping(rootMapping.getTarget(), rootMapping);
		
		Mapping inlineMapping = new Mapping(URI.create("http://example.com/schemas/inline.json#/properties/inline"), new ClassName("com.example.test.schemas", "Inline"));
		generator.addMapping(inlineMapping.getTarget(), inlineMapping);
		
		main.generate(Arrays.asList(rootMapping.getTarget(), inlineMapping.getTarget()));
		
		Path generatedTypeFile = outputDirectory.resolve("com/example/test/schemas/WithInline.java"); 
		assertTrue(Files.exists(generatedTypeFile));
		System.out.println(new String(Files.readAllBytes(generatedTypeFile), StandardCharsets.UTF_8));
		Path generatedInlineTypeFile = outputDirectory.resolve("com/example/test/schemas/Inline.java"); 
		assertTrue(Files.exists(generatedInlineTypeFile));
	}	
}
