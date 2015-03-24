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
	
	public PojoPropertyGenerator createPropertyGenerator(URI type, String propertyName) throws CodeGenerationException {
		ClassName className = generator.generate(type);
		if (className == null) {
			throw new InvalidTypeReferenceException(type);
		}
		return createPropertyGenerator(className, propertyName);
	}
	
	public PojoPropertyGenerator createPropertyGenerator(ClassName className, String propertyName) throws CodeGenerationException {
		return new SimplePojoPropertyGenerator(className, propertyName);
	}
}