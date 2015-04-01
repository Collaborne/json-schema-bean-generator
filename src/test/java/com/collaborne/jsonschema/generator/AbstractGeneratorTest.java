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
package com.collaborne.jsonschema.generator;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.UUID;

import org.junit.Test;

import com.collaborne.jsonschema.generator.java.ClassName;

public class AbstractGeneratorTest {
	private static class DummyGenerator extends AbstractGenerator {
		@Override
		public ClassName generate(URI type) throws CodeGenerationException {
			throw new UnsupportedOperationException("Generator#generate() is not implemented");
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void getDefaultPackageNameThrowsIllegalArgumentExceptionForTypeWithInvalidJsonPointerFragment() {
		AbstractGenerator generator = new DummyGenerator();
		generator.getDefaultPackageName(URI.create("http://example.com/#not-json-pointer-fragment"));
	}

	@Test
	public void getDefaultPackageNameNoneReturnsFeatureValue() {
		URI typeUri = URI.create("http://example.com/path#/pointer");
		String fallbackDefaultPackageName = UUID.randomUUID().toString();

		AbstractGenerator generator = new DummyGenerator();
		generator.setFeature(Generator.FEATURE_DEFAULT_PACKAGE_NAME, fallbackDefaultPackageName);
		assertEquals(fallbackDefaultPackageName, generator.getDefaultPackageName(typeUri));
	}

	@Test
	public void getDefaultPackageNameReturnsExactMapping() {
		URI typeUri = URI.create("http://example.com/path#/pointer");
		String defaultPackageName = UUID.randomUUID().toString();

		AbstractGenerator generator = new DummyGenerator();
		generator.addDefaultPackageName(typeUri, defaultPackageName);
		assertEquals(defaultPackageName, generator.getDefaultPackageName(typeUri));
	}

	@Test
	public void getDefaultPackageNameReturnsMappedToFragment() {
		URI packageUri = URI.create("http://example.com/path");
		URI typeUri = packageUri.resolve("#/pointer");
		String defaultPackageName = UUID.randomUUID().toString();

		AbstractGenerator generator = new DummyGenerator();
		generator.addDefaultPackageName(packageUri, defaultPackageName);
		assertEquals(defaultPackageName, generator.getDefaultPackageName(typeUri));
	}
}
