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

import java.io.IOException;
import java.io.StringReader;

import org.junit.Before;
import org.junit.Test;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoStringGeneratorTest {
	private JsonNodeReader jsonNodeReader;
	private SchemaLoader schemaLoader;
	
	@Before
	public void setUp() {
		jsonNodeReader = new JsonNodeReader();
		schemaLoader = new SchemaLoader();
	}
	
	@Test
	public void generateNoEnumReturnsString() throws IOException, CodeGenerationException {
		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"type\": \"string\"}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		PojoStringGenerator generator = new PojoStringGenerator();
		ClassName generated = generator.generate(null, schema, null);
		assertEquals(ClassName.create(String.class), generated);
	}
}
