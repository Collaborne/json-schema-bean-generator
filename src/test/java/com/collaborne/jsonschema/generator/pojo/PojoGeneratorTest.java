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
package com.collaborne.jsonschema.generator.pojo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
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
			protected void writeSource(URI type, ClassName className, Buffer buffer) throws java.io.IOException {
				writeSourceCalled.set(true);
			}

			@Override
			protected SchemaTree getSchema(SchemaLoader schemaLoader, URI uri) throws ProcessingException, JsonPointerException {
				return schema;
			}
		};

		ClassName className = generator.generateInternal(mapping.getTarget(), mapping);
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
			protected void writeSource(URI type, ClassName className, Buffer buffer) throws java.io.IOException {
				writeSourceCalled.set(true);
			}

			@Override
			protected SchemaTree getSchema(SchemaLoader schemaLoader, URI uri) throws ProcessingException, JsonPointerException {
				return schema;
			}
		};

		ClassName className = generator.generateInternal(mapping.getTarget(), mapping);
		assertEquals(mapping.getClassName(), className);
		assertFalse(writeSourceCalled.get());
	}
}
