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

import static org.junit.Assert.assertFalse;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.collaborne.jsonschema.generator.AbstractGenerator;
import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.Generator;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.collaborne.jsonschema.generator.model.Mappings;

public class GeneratorDriverTest {
	@Test
	public void addMappingsWithNoMappingsWorks() {
		final AtomicBoolean addMappingCalled = new AtomicBoolean();
		Generator generator = new AbstractGenerator() {
			@Override
			public ClassName generate(URI type) throws CodeGenerationException {
				throw new UnsupportedOperationException("Type1427895891163#generate() is not implemented");
			}

			@Override
			public void addMapping(URI type, Mapping mapping) {
				addMappingCalled.set(true);
			}
		};
		GeneratorDriver driver = new GeneratorDriver(generator);
		driver.addMappings(new Mappings());
		assertFalse(addMappingCalled.get());
	}

	@Test(expected=IllegalArgumentException.class)
	public void addMappingsNoClassNameThrowsIllegalArgumentException() {
		Generator generator = new AbstractGenerator() {
			@Override
			public ClassName generate(URI type) throws CodeGenerationException {
				throw new UnsupportedOperationException("Type1427895891163#generate() is not implemented");
			}
		};

		Mapping mapping = new Mapping();
		mapping.setTarget(URI.create("http://example.com/#"));
		Mappings mappings = new Mappings();
		mappings.setMappings(Arrays.asList(mapping));

		GeneratorDriver driver = new GeneratorDriver(generator);
		driver.addMappings(mappings);
	}
}
