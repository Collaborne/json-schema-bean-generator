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
package com.collaborne.jsonschema.generator.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.UUID;

import org.junit.Test;

import com.collaborne.jsonschema.generator.java.ClassName;

public class MappingTest {

	@Test
	public void noArgCtorSetsFields() {
		Mapping mapping = new Mapping();
		assertNull(mapping.getTarget());
		assertNull(mapping.getClassName());
	}

	@Test
	public void ctorSetsFields() {
		URI target = URI.create("http://example.com/#" + UUID.randomUUID().toString());
		ClassName className = new ClassName(UUID.randomUUID().toString(), UUID.randomUUID().toString());
		Mapping mapping = new Mapping(target, className);
		assertEquals(target, mapping.getTarget());
		assertEquals(className, mapping.getClassName());
	}

	@Test
	public void getGeneratedClassNameReturnsClassNameIfNotSet() {
		ClassName className = new ClassName(UUID.randomUUID().toString(), UUID.randomUUID().toString());
		Mapping mapping = new Mapping(URI.create("http://example.com/#"), className);
		assertEquals(className, mapping.getGeneratedClassName());
	}
}
