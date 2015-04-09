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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.JavaWriter.Block;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Modifier;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoStringGenerator extends AbstractPojoTypeGenerator {
	private interface EnumGenerator {
		void generateImports(JavaWriter writer) throws IOException;
		void generateEnumValue(String value, JavaWriter writer) throws IOException;
		void generateAdditionalCode(JavaWriter writer) throws IOException;
	}

	private static abstract class AbstractEnumGenerator implements EnumGenerator {
		private final ClassName className;
		private final Visibility constructorVisibility;

		protected AbstractEnumGenerator(ClassName className, Visibility constructorVisibility) {
			this.className = className;
			this.constructorVisibility = constructorVisibility;
		}

		public ClassName getClassName() {
			return className;
		}

		@Override
		public void generateImports(JavaWriter writer) throws IOException {
			// Nothing by default
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			ClassName stringClassName = ClassName.create(String.class);

			writer.writeEmptyLine();
			writer.writeField(Visibility.PRIVATE, EnumSet.of(Modifier.FINAL), stringClassName, "value", Block.empty());

			// Create the constructor
			writer.writeConstructorBodyStart(constructorVisibility, getClassName(), stringClassName, "value");
			writer.writeCode("this.value = value;");
			writer.writeMethodBodyEnd();

			// Create an accessor for the value
			writer.writeMethodBodyStart(Visibility.PUBLIC, stringClassName, "getValue");
			writer.writeCode("return value;");
			writer.writeMethodBodyEnd();

			// Create a nice #toString() that uses the value
			writer.writeAnnotation(ClassName.create(Override.class));
			writer.writeMethodBodyStart(Visibility.PUBLIC, stringClassName, "toString");
			writer.writeCode("return getValue();");
			writer.writeMethodBodyEnd();

			// Create a #parse() method
			writer.writeMethodBodyStart(Visibility.PUBLIC, EnumSet.of(Modifier.STATIC), className, "parse", stringClassName, "stringValue");
			writer.writeCode(
					"for (" + className + " value : values()) {",
					"\tif (value.value.equals(stringValue)) {",
					"\t\treturn value;",
					"\t}",
					"}",
					"throw new IllegalArgumentException(\"Unknown value \" + stringValue);"
					);
			writer.writeMethodBodyEnd();
		}
	}

	private static class ClassEnumGenerator extends AbstractEnumGenerator {
		private final List<String> generatedValues = new ArrayList<>();

		public ClassEnumGenerator(ClassName className) {
			// XXX: Visibility of the constructor should somehow get linked to the additionalProperties or such?
			super(className, Visibility.PUBLIC);
		}

		@Override
		public void generateImports(JavaWriter writer) throws IOException {
			super.generateImports(writer);
			writer.writeImport(ClassName.create(Objects.class));
			writer.writeImport(ClassName.create(Arrays.class));
			writer.writeImport(ClassName.create(List.class));
		}

		@Override
		public void generateEnumValue(String value, JavaWriter writer) throws IOException {
			String generatedValue = value.toUpperCase(Locale.ENGLISH);
			generatedValues.add(generatedValue);
			writer.writeCode("public static final " + getClassName().getRawClassName() + " " + generatedValue + " = new " + getClassName().getRawClassName() + "(\"" + value + "\");");
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			super.generateAdditionalCode(writer);

			// Create #hashCode() and #equals()
			writer.writeAnnotation(ClassName.create(Override.class));
			writer.writeMethodBodyStart(Visibility.PUBLIC, ClassName.create(Integer.TYPE), "hashCode");
			writer.writeCode("return Objects.hash(value);");
			writer.writeMethodBodyEnd();

			writer.writeAnnotation(ClassName.create(Override.class));
			writer.writeMethodBodyStart(Visibility.PUBLIC, ClassName.create(Boolean.TYPE), "equals", ClassName.create(Object.class), "obj");
			writer.writeCode(
					"if (!(obj instanceof " + getClassName().getRawClassName() + ")) {",
					"\treturn false;",
					"}",
					"return Objects.equals(value, ((" + getClassName().getRawClassName() + ") obj).value);");
			writer.writeMethodBodyEnd();

			// FIXME: Should use an array here, to match Enum#values()
			ClassName listOfClassName = ClassName.create(List.class, getClassName());
			writer.writeMethodBodyStart(Visibility.PUBLIC, EnumSet.of(Modifier.STATIC), listOfClassName, "values");
			writer.writeCode("return Arrays.asList(");
			writer.pushIndentLevel();
			for (Iterator<String> it = generatedValues.iterator(); it.hasNext(); ) {
				String generatedValue = it.next();
				writer.writeIndent();
				writer.write(generatedValue);
				if (it.hasNext()) {
					writer.write(",");
				}
				writer.write("\n");
			}
			writer.popIndentLevel();
			writer.writeCode(");");
			writer.writeMethodBodyEnd();
		}
	}

	private static class EnumEnumGenerator extends AbstractEnumGenerator {
		private boolean wroteOneValue = false;

		public EnumEnumGenerator(ClassName className) {
			super(className, Visibility.PRIVATE);
		}

		@Override
		public void generateEnumValue(String value, JavaWriter writer) throws IOException {
			if (wroteOneValue) {
				writer.write(",\n");
			}
			writer.writeIndent();
			writer.write(value.toUpperCase(Locale.ENGLISH) + "(\"" + value + "\")");
			wroteOneValue = true;
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			assert wroteOneValue : "Invalid code generated: Missing enum values";
			if (wroteOneValue) {
				writer.write(";\n");
			}

			super.generateAdditionalCode(writer);
		}
	}

	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		if (!schema.getNode().hasNonNull("enum")) {
			// Not an enum-ish string, so just map it to that.
			return ClassName.create(String.class);
		}

		return super.generate(context, schema, writer);
	}

	@Override
	protected void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		JsonNode enumValues = schema.getNode().get("enum");
		if (!enumValues.isArray()) {
			throw new CodeGenerationException(context.getType(), "Expected 'array' for 'enum', but have " + enumValues);
		}

		ClassName wantedGeneratedClassName = context.getMapping().getGeneratedClassName();
		EnumGenerator enumGenerator;
		Kind enumStyle = context.getMapping().getEnumStyle();
		if (enumStyle == null) {
			enumStyle = context.getGenerator().getFeature(PojoGenerator.FEATURE_ENUM_STYLE);
		}
		switch (enumStyle) {
		case CLASS:
			enumGenerator = new ClassEnumGenerator(wantedGeneratedClassName);
			break;
		case ENUM:
			enumGenerator = new EnumEnumGenerator(wantedGeneratedClassName);
			break;
		default:
			throw new CodeGenerationException(context.getType(), new IllegalArgumentException("Invalid enum style: " + enumStyle));
		}

		enumGenerator.generateImports(writer);

		writeSchemaDocumentation(schema, writer);
		writer.writeClassStart(wantedGeneratedClassName, context.getMapping().getExtends(), context.getMapping().getImplements(), enumStyle, Visibility.PUBLIC);
		try {
			for (JsonNode enumValue : enumValues) {
				if (!enumValue.isTextual()) {
					throw new CodeGenerationException(context.getType(), "Expected textual 'enum' values, but have " + enumValue);
				}
				String value = enumValue.textValue();
				enumGenerator.generateEnumValue(value, writer);
			}
			enumGenerator.generateAdditionalCode(writer);
		} finally {
			writer.writeClassEnd();
		}
	}
}
