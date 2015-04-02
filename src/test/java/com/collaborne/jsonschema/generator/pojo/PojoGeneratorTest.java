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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoGeneratorTest {
	private static class TestClass {
		/* Nothing */
	}

	private JsonNodeReader jsonNodeReader;
	private SchemaLoader schemaLoader;

	@Before
	public void setUp() {
		jsonNodeReader = new JsonNodeReader();
		schemaLoader = new SchemaLoader();
	}

	@Test
	public void generateInternalPrimitiveTypeReturnsPrimitiveTypeWithoutGeneration() throws CodeGenerationException, IOException {
		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"type\": \"string\"}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		Mapping mapping = new Mapping(URI.create("http://example.com/type.json#"), ClassName.create(Integer.TYPE));
		final AtomicBoolean writeSourceCalled = new AtomicBoolean();
		PojoGenerator generator = new PojoGenerator(null, null, null) {
			@Override
			protected void writeSource(URI type, ClassName className, Buffer buffer) throws IOException {
				writeSourceCalled.set(true);
			}
		};

		ClassName className = generator.generateInternal(mapping.getTarget(), schema, mapping);
		assertEquals(mapping.getClassName(), className);
		assertFalse(writeSourceCalled.get());
	}

	@Test
	public void generateInternalExistingTypeReturnsExistingTypeWithoutGeneration() throws CodeGenerationException, IOException {
		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"type\": \"string\"}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		Mapping mapping = new Mapping(URI.create("http://example.com/type.json#"), ClassName.create(TestClass.class));
		final AtomicBoolean writeSourceCalled = new AtomicBoolean();
		PojoGenerator generator = new PojoGenerator(null, null, null) {
			@Override
			protected void writeSource(URI type, ClassName className, Buffer buffer) throws IOException {
				writeSourceCalled.set(true);
			}
		};

		ClassName className = generator.generateInternal(mapping.getTarget(), schema, mapping);
		assertEquals(mapping.getClassName(), className);
		assertFalse(writeSourceCalled.get());
	}

	@Test
	public void generateMappingForTypeSetsMappingTargetToType() {
		URI type = URI.create("http://example.com/#/" + UUID.randomUUID().toString());
		PojoGenerator generator = new PojoGenerator(null, null, null);
		Mapping mapping = generator.generateMapping(type);
		assertEquals(type, mapping.getTarget());
	}

	@Test
	public void generateMappingUsesDefaultPackageFeature() {
		URI type = URI.create("http://example.com/#/" + UUID.randomUUID().toString());
		PojoGenerator generator = new PojoGenerator(null, null, null);
		String packageName = "some.package" + UUID.randomUUID().toString();
		generator.setFeature(PojoGenerator.FEATURE_DEFAULT_PACKAGE_NAME, packageName);
		Mapping mapping = generator.generateMapping(type);
		assertEquals(packageName, mapping.getClassName().getPackageName());
	}

	@Test
	public void getSchemaTypeReturnsTypeKeyword() throws IOException, CodeGenerationException {
		URI type = URI.create("http://example.com/#");
		PojoGenerator generator = new PojoGenerator(null, null, null);

		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"type\": \"string\"}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		assertEquals("string", generator.getSchemaType(type, schema));
	}

	@Test
	public void getSchemaTypeReturnsObjectForEmptySchema() throws IOException, CodeGenerationException {
		URI type = URI.create("http://example.com/#");
		PojoGenerator generator = new PojoGenerator(null, null, null);

		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		assertEquals("object", generator.getSchemaType(type, schema));
	}

	@Test
	public void getSchemaTypeReturnsAllOf() throws IOException, CodeGenerationException {
		URI type = URI.create("http://example.com/#");
		PojoGenerator generator = new PojoGenerator(null, null, null);

		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"allOf\": []}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		assertEquals("allOf", generator.getSchemaType(type, schema));
	}

	@Test
	public void getSchemaTypeReturnsAnyOf() throws IOException, CodeGenerationException {
		URI type = URI.create("http://example.com/#");
		PojoGenerator generator = new PojoGenerator(null, null, null);

		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"anyOf\": []}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		assertEquals("anyOf", generator.getSchemaType(type, schema));
	}

	@Test
	public void getSchemaTypeReturnsOneOf() throws IOException, CodeGenerationException {
		URI type = URI.create("http://example.com/#");
		PojoGenerator generator = new PojoGenerator(null, null, null);

		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"oneOf\": []}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		assertEquals("oneOf", generator.getSchemaType(type, schema));
	}

	@Test(expected=CodeGenerationException.class)
	public void getSchemaTypeThrowsCodeGenerationExceptionForMultipleAggregations() throws IOException, CodeGenerationException {
		URI type = URI.create("http://example.com/#");
		PojoGenerator generator = new PojoGenerator(null, null, null);

		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"allOf\": [], \"anyOf\": [], \"oneOf\": []}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		generator.getSchemaType(type, schema);
	}
}
