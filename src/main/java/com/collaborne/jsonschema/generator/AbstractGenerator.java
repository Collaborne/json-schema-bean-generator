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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.model.Mapping;
import com.github.fge.jsonschema.core.load.SchemaLoader;

public abstract class AbstractGenerator implements Generator {
	private final Logger logger = LoggerFactory.getLogger(AbstractGenerator.class);

	private final Map<String, Object> features = new HashMap<>();
	private Map<URI, Mapping> mappings = new HashMap<>();
	private Path outputDirectory;
	private SchemaLoader schemaLoader;

	@Override
	public <T>T getFeature(Feature<T> feature) {
		return feature.get(features);
	}
	
	@Override
	public <T>T setFeature(Feature<T> feature, T value) {
		return feature.set(features, value);
	}

	@Override
	public void addMapping(URI type, Mapping mapping) {
		if (!type.isAbsolute()) {
			logger.warn("{}: Adding mapping for non-absolute type");
		}
		mappings.put(type, mapping);
	}
	
	protected Path getOutputDirectory() {
		return outputDirectory;
	}
	
	@Override
	public void setOutputDirectory(Path outputDirectory) {
		this.outputDirectory = outputDirectory;
	}
	
	@Override
	public void setSchemaLoader(SchemaLoader schemaLoader) {
		this.schemaLoader = schemaLoader;
	}
	
	protected SchemaLoader getSchemaLoader() {
		return schemaLoader;
	}
	
	/**
	 * Get an existing mapping for the given {@code type}.
	 * 
	 * If no mapping is known for this type, {@code null} is returned.
	 * 
	 * @param type
	 * @return
	 */
	protected Mapping getMapping(URI type) {
		return mappings.get(type);
	}
}
