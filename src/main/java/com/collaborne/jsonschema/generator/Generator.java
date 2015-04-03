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

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.github.fge.jsonschema.core.load.SchemaLoader;

public interface Generator {
	public static class Feature<T> {
		private final String uri;
		private final Class<T> requiredType;
		private final T defaultValue;
		
		public Feature(String uri, Class<T> requiredType) {
			this(uri, requiredType, null);
		}
		
		public Feature(String uri, Class<T> requiredType, T defaultValue) {
			this.uri = uri;
			this.requiredType = requiredType;
			this.defaultValue = defaultValue;
		}	

		public String getUri() {
			return uri;
		}

		public Class<T> getRequiredType() {
			return requiredType;
		}

		public T getDefaultValue() {
			return defaultValue;
		}

		protected T get(Map<String, Object> featureMap) {
			Object v = featureMap.get(uri);
			return checkCast(v);
		}
		
		protected T set(Map<String, Object> featureMap, T value) {
			Object v = featureMap.put(uri, value);
			return checkCast(v);
		}
		
		protected T checkCast(Object v) {
			if (v == null) {
				return defaultValue;
			}
			if (!requiredType.isInstance(v)) {
				throw new IllegalStateException("Expected feature " + uri + " to be of type " + requiredType + ", but found " + v.getClass().getName());
			}
			return requiredType.cast(v);
		}
	}

	/** The name of the package to use for anonymous types */
	public static final Feature<String> FEATURE_DEFAULT_PACKAGE_NAME = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/package-name", String.class, "anonymous");
	
	/**
	 * Set the directory for any output
	 * 
	 * @param outputDirectory
	 */
	// TODO: builder
	void setOutputDirectory(Path outputDirectory);
	
	/**
	 * Add a mapping for the given {@code type}.
	 *
	 * If a mapping already exists for the type, the given mapping will override
	 * it.
	 *
	 * @param type
	 * @param mapping
	 */
	// TODO: builder
	void addMapping(URI type, Mapping mapping);

	// TODO: builder
	void setSchemaLoader(SchemaLoader schemaLoader);

	// TODO: builder
	void addDefaultPackageName(URI baseUri, String packageName);

	/**
	 * Generate code for the given {@code type}, and return the class name of it.
	 * 
	 * This method can be invoked multiple times with the same {@code type}, and will only do the generation once. Additional
	 * calls will return the name of the previously generated class.
	 * 
	 * If the return value is {@code null} then the type has the value {@code "null"} for the JSON-Schema {@code 'type' keyword}.
	 * This itself is not an error, but if the type is referenced in a property context that reference is invalid.
	 * 
	 * @param type
	 * @return the name of the generated class, or {@code null} if this type does not require a class
	 * @throws CodeGenerationException if generation failed
	 */
	ClassName generate(URI type) throws CodeGenerationException;
	
	<T>T getFeature(Feature<T> feature);
	<T>T setFeature(Feature<T> feature, T value);
}
