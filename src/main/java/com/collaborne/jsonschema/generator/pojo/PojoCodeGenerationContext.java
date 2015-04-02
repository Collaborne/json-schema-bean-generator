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

import java.net.URI;

import javax.annotation.Nonnull;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.Generator;
import com.collaborne.jsonschema.generator.InvalidTypeReferenceException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;

class PojoCodeGenerationContext {
	private final Generator generator;
	private final Mapping mapping;
	
	public PojoCodeGenerationContext(@Nonnull Generator generator, @Nonnull Mapping mapping) {
		this.generator = generator;
		this.mapping = mapping;
	}
	
	public Generator getGenerator() {
		return generator;
	}
	
	public Mapping getMapping() {
		return mapping;
	}
	
	public URI getType() {
		return mapping.getTarget();
	}
	
	/**
	 * Create a {@link PojoPropertyGenerator} for the given {@code propertyName} and {@code type}.
	 *
	 * This method invokes {@link Generator#generate(URI)} for the given {@code type}, and then
	 * uses {@link #createPropertyGenerator(ClassName, String, String)} if the generation was successful.
	 *
	 * @param type
	 * @param propertyName
	 * @param defaultValue the default value, or {@code null} if none.
	 * @return
	 * @throws CodeGenerationException
	 * @see {@link #createPropertyGenerator(ClassName, String, String)}
	 */
	public PojoPropertyGenerator createPropertyGenerator(URI type, String propertyName, String defaultValue) throws CodeGenerationException {
		ClassName className = generator.generate(type);
		if (className == null) {
			throw new InvalidTypeReferenceException(type);
		}
		return createPropertyGenerator(className, propertyName, defaultValue);
	}
	
	/**
	 * Create a {@link PojoPropertyGenerator} for the given {@code propertyName} and {@code className}.
	 *
	 * @param className
	 * @param propertyName
	 * @param defaultValue the default value, or {@code null} if none.
	 * @return
	 * @throws CodeGenerationException
	 */
	public PojoPropertyGenerator createPropertyGenerator(ClassName className, String propertyName, String defaultValue) throws CodeGenerationException {
		return new SimplePojoPropertyGenerator(className, propertyName, defaultValue);
	}
}