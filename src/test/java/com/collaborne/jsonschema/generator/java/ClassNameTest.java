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
package com.collaborne.jsonschema.generator.java;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ClassNameTest {
	private static class TestClass {
		/* Nothing */
	}

	@Test
	public void createWithInnerClassReturnsProperName() throws ClassNotFoundException {
		ClassName className = ClassName.create(TestClass.class);
		Class<?> foundClass = Class.forName(className.toString());
		assertEquals(TestClass.class, foundClass);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ClassName.class)
			.withPrefabValues(ClassName.class, new ClassName("com.example", "Foo"), new ClassName("com.example.other", "Bar"))
			.verify();
	}

	@Test
	public void parseSimple() {
		ClassName className = ClassName.parse("java.lang.String");
		assertEquals("java.lang", className.getPackageName());
		assertEquals("String", className.getRawClassName());
		assertNull(className.getTypeArguments());
	}

	@Test
	public void parseNoPackageName() {
		ClassName className = ClassName.parse("String");
		assertEquals("", className.getPackageName());
		assertEquals("String", className.getRawClassName());
		assertNull(className.getTypeArguments());
	}

	@Test
	public void parseGenericTypeSingleTypeArgument() {
		ClassName className = ClassName.parse("java.util.Map<java.lang.String>");
		assertEquals("java.util", className.getPackageName());
		assertEquals("Map", className.getRawClassName());
		assertArrayEquals(new ClassName[] { ClassName.create(String.class) }, className.getTypeArguments());
	}

	@Test
	public void parseGenericTypeMultipleTypeArguments() {
		ClassName className = ClassName.parse("java.util.Map<java.lang.String,java.lang.Integer>");
		assertEquals("java.util", className.getPackageName());
		assertEquals("Map", className.getRawClassName());
		assertArrayEquals(new ClassName[] { ClassName.create(String.class), ClassName.create(Integer.class) }, className.getTypeArguments());
	}

	@Test
	public void parseGenericTypeMultipleTypeArgumentsTrimsTypeArguments() {
		ClassName className = ClassName.parse("java.util.Map< java.lang.String , java.lang.Integer >");
		assertEquals("java.util", className.getPackageName());
		assertEquals("Map", className.getRawClassName());
		assertArrayEquals(new ClassName[] { ClassName.create(String.class), ClassName.create(Integer.class) }, className.getTypeArguments());
	}
}
