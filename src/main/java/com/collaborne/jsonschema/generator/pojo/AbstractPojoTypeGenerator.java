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

import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.annotations.VisibleForTesting;

abstract class AbstractPojoTypeGenerator implements PojoTypeGenerator {
	protected interface SchemaVisitor<T extends Exception> {
		// FIXME: should be a SchemaTree, not a JsonNode
		void visitSchema(URI type, JsonNode schemaNode) throws T;
		void visitSchema(URI type) throws T;
	}
	
	private final Kind kind;
	private final Visibility visibility;
	
	@Inject
	@VisibleForTesting
	protected AbstractPojoTypeGenerator(Kind kind, Visibility visibility) {
		this.kind = kind;
		this.visibility = visibility;
	}
	
	protected Kind getKind() {
		return kind;
	}
	
	protected Visibility getVisibility() {
		return visibility;
	}
	
	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		ClassName className = context.getMapping().getClassName();
		writer.writePackage(className);

		generateType(context, schema, writer);
		
		return className;
	}
	
	protected abstract void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException;
	
	protected <T extends Exception> boolean visitSchema(URI elementUri, JsonNode element, SchemaVisitor<T> visitor) throws T {
		if (!element.isContainerNode()) {
			return false;
		}
		
		// We handle "id" here in the same way as the json-schema-validator: it's fairly non-trust-worthy.
		// In a properly written document the id is useless, as we already had that with a $ref, and you cannot just modify things in place.
		if (element.hasNonNull("$ref")) {
			// A reference to something else.
			String refValue = element.get("$ref").textValue();
			visitor.visitSchema(elementUri.resolve(refValue));
		} else {
			visitor.visitSchema(elementUri, element);
		}
		
		return true;
	}
}