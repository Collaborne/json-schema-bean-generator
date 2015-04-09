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

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.annotation.Generated;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: extract interface, this is really the "PrettyJavaWriter"
// TODO: where should the java-awareness lie? Is this not something on top of the purely syntactic writing of java code? And how far should it go?
public class JavaWriter implements Closeable {
	public interface Block {
		void execute() throws IOException;

		static Block empty() {
			return () -> {};
		}
	}

	private final Logger logger = LoggerFactory.getLogger(JavaWriter.class);
	private final BufferedWriter writer;
	// TODO: allow for different indents (like 4 spaces, 2 spaces, etc)
	private String indent = "\t";
	private int indentLevel = 0;
	private String packageName = "";
	private Stack<ClassName> currentClassNames = new Stack<>();
	/** Map of all imports: package.rawClassName to rawClassName */
	private Map<String, String> importedClassNames = new HashMap<>();
	private boolean importsFlushed = false;
	private boolean skipNextEmptyLine = false;
	
	public JavaWriter(BufferedWriter writer) {
		this.writer = writer;
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}
	
	public void write(String text) throws IOException {
		writer.write(text);
	}

	public void pushIndentLevel() {
		indentLevel++;
	}
	
	public void popIndentLevel() {
		indentLevel--;
	}
	
	public void writeIndent() throws IOException {
		for (int i = 0; i < indentLevel; i++) {
			write(indent);
		}
	}
	
	public void writePackage(ClassName fqcn) throws IOException {
		String packageName = fqcn.getPackageName();
		if (!packageName.isEmpty()) {
			write("package ");
			write(packageName);
			write(";\n");
		}
		// Remember for later
		this.packageName = packageName;
	}
	
	/**
	 * @throws IOException
	 */
	public void writeImport(ClassName fqcn) throws IOException {
		if (importsFlushed) {
			// Ignore the request with a warning: the generated code will have to use the full name.
			logger.warn("Cannot add import for " + fqcn + ": imports have been flushed already");
			return;
		}
		String packageName = fqcn.getPackageName();
		if (packageName.isEmpty()) {
			// Cannot import from the default package
			return;
		}
		if (this.packageName.equals(packageName) || "java.lang".equals(packageName)) {
			// Skip our package and java.lang
			return;
		}
		
		String rawClassName = fqcn.getRawClassName();
		if (!importedClassNames.values().contains(rawClassName)) {
			// Not yet imported, so we can pick this one
			String importClassName = packageName + "." + rawClassName;
			importedClassNames.put(importClassName, rawClassName);
		}

		if (fqcn.getTypeArguments() != null) {
			// Try importing the type arguments as well
			for (ClassName typeArgument : fqcn.getTypeArguments()) {
				writeImport(typeArgument);
			}
		}
	}

	protected void flushImports() throws IOException {
		if (importsFlushed || importedClassNames.isEmpty()) {
			return;
		}

		// Write the imported class names, sorted by name
		List<String> importClassNames = importedClassNames.keySet().stream().sorted().collect(Collectors.toList());
		
		writeEmptyLine();
		for (String importClassName : importClassNames) {
			writeImportForce(ClassName.parse(importClassName));
		}
		importsFlushed = true;
	}
	
	protected String getAvailableShortName(ClassName fqcn) {
		String className = null;
		String packageName = fqcn.getPackageName();
		String rawClassName = fqcn.getRawClassName();
		if (packageName.isEmpty()) {
			className = rawClassName;
		} else if (packageName.equals(this.packageName) || "java.lang".equals(packageName)) {
			// Always available
			className = fqcn.getRawClassName();
		} else {
			String importClassName = packageName + "." + rawClassName;
			className = importedClassNames.get(importClassName);
		}
		
		StringBuilder sb = new StringBuilder();
		if (className == null) {
			sb.append(packageName);
			sb.append(".");
			sb.append(rawClassName);
		} else {
			sb.append(className);
		}
		
		// Add the type arguments
		ClassName[] typeArguments = fqcn.getTypeArguments();
		if (typeArguments != null && typeArguments.length > 0) {
			sb.append("<");
			for (int i = 0; i < typeArguments.length; i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append(getAvailableShortName(typeArguments[i]));
			}
			sb.append(">");
		}
		return sb.toString();
	}
	
	public void writeImportForce(ClassName fqcn) throws IOException {
		if (!fqcn.getPackageName().isEmpty()) {
			write("import ");
			write(fqcn.getPackageName());
			write(".");
			write(fqcn.getRawClassName());
			write(";\n");
		}
	}
	
	// XXX: This should get collected as well, and flushed in #flushImports()
	public void writeImport(ClassName fqcn, String methodName) throws IOException {
		write("import static ");
		if (!fqcn.getPackageName().isEmpty()) {
			write(fqcn.getPackageName());
			write(".");
		}
		write(fqcn.getRawClassName());
		write(".");
		write(methodName);
		write(";\n");
	}

	public void writeClassStart(ClassName fqcn, ClassName extendedClass, List<ClassName> implementedInterfaces, Kind kind, Visibility visibility) throws IOException {
		writeClassStart(fqcn, extendedClass, implementedInterfaces, kind, visibility, EnumSet.noneOf(Modifier.class));
	}

	public void writeClassStart(ClassName fqcn, ClassName extendedClass, List<ClassName> implementedInterfaces, Kind kind, Visibility visibility, Collection<Modifier> modifiers) throws IOException {
		ClassName generatedAnnotationClassName = ClassName.create(Generated.class);
		writeImport(generatedAnnotationClassName);
		if (extendedClass != null) {
			writeImport(extendedClass);
		}
		if (implementedInterfaces != null) {
			for (ClassName implementedInterface : implementedInterfaces) {
				writeImport(implementedInterface);
			}
		}
		flushImports();

		// XXX: visibility in the mapping? options ("all public", "all minimum?")
		writeAnnotation(generatedAnnotationClassName, '"' + getClass().getCanonicalName() + '"');
		writeIndent();
		write(visibility.getValue());
		write(" ");
		if (modifiers != null) {
			Set<Modifier> writtenModifiers = EnumSet.noneOf(Modifier.class);
			for (Modifier modifier : modifiers) {
				if (!writtenModifiers.add(modifier)) {
					logger.warn("Duplicate modifier {} for {}", modifier, fqcn);
					continue;
				}
				write(modifier.getValue());
				write(" ");
			}
		}
		write(kind.getValue());
		write(" ");
		// XXX: generating generic types won't work with just this
		write(fqcn.getRawClassName());

		// Write extended class, if any
		if (extendedClass != null) {
			write(" ");
			write("extends");
			write(" ");
			writeClassName(extendedClass);
		}

		// Write implemented interfaces, if any
		if (implementedInterfaces != null && !implementedInterfaces.isEmpty()) {
			write(" ");
			write("implements");
			write(" ");
			for (int i = 0; i < implementedInterfaces.size(); i++) {
				if (i > 0) {
					write(", ");
				}
				writeClassName(implementedInterfaces.get(i));
			}
		}
		write(" {\n");
		pushIndentLevel();
		currentClassNames.push(fqcn);
	}
	
	public void writeClassEnd() throws IOException {
		popIndentLevel();
		writeIndent();
		write("}\n");
		currentClassNames.pop();
	}
	
	public void writeField(Visibility visibility, ClassName className, String fieldName) throws IOException {
		writeField(visibility, className, fieldName, Block.empty());
	}

	public void writeField(Visibility visibility, ClassName className, String fieldName, @Nonnull Block block) throws IOException {
		writeField(visibility, EnumSet.noneOf(Modifier.class), className, fieldName, block);
	}

	public void writeField(Visibility visibility, Collection<Modifier> modifiers, ClassName className, String fieldName, @Nonnull Block block) throws IOException {
		writeIndent();
		write(visibility.getValue());
		write(" ");
		if (modifiers != null) {
			Set<Modifier> writtenModifiers = EnumSet.noneOf(Modifier.class);
			for (Modifier modifier : modifiers) {
				if (!writtenModifiers.add(modifier)) {
					logger.warn("Duplicate modifier {} for {}", modifier, fieldName);
					continue;
				}
				write(modifier.getValue());
				write(" ");
			}
		}
		writeClassName(className);
		write(" ");
		write(fieldName);
		block.execute();
		write(";\n");
	}

	public void writeConstructorBodyStart(Visibility visibility, ClassName className, Object... typesAndValues) throws IOException {
		writeMethodBodyStart(visibility, null, className.getRawClassName(), typesAndValues);
	}

	// FIXME: declaration is really weird, should introduce a dedicated type for (ClassName, String)
	public void writeMethodBodyStart(Visibility visibility, ClassName className, String methodName, Object... typesAndValues) throws IOException {
		writeMethodBodyStart(visibility, EnumSet.noneOf(Modifier.class), className, methodName, typesAndValues);
	}

	public void writeMethodBodyStart(Visibility visibility, Collection<Modifier> modifiers, ClassName className, String methodName, Object... typesAndValues) throws IOException {
		assert typesAndValues == null || typesAndValues.length % 2 == 0;
		writeEmptyLine();
		writeIndent();
		write(visibility.getValue());
		write(" ");
		if (modifiers != null) {
			Set<Modifier> writtenModifiers = EnumSet.noneOf(Modifier.class);
			for (Modifier modifier : modifiers) {
				if (!writtenModifiers.add(modifier)) {
					logger.warn("Duplicate modifier {} for {}", modifier, methodName);
					continue;
				}
				write(modifier.getValue());
				write(" ");
			}
		}
		if (className != null) {
			// Constructors do not have a return type
			writeClassName(className);
			write(" ");
		}
		write(methodName);
		write("(");
		if (typesAndValues != null) {
			for (int i = 0; i < typesAndValues.length; i += 2) {
				if (i > 0) {
					write(", ");
				}
				writeMethodBodyStartFormalArgument((ClassName) typesAndValues[i], (String) typesAndValues[i + 1]);
			}
		};
		write(") {\n");
		pushIndentLevel();
	}
	
	public void writeCode(String... lines) throws IOException {
		assert lines != null;
		for (String line : lines) {
			writeIndent();
			write(line);
			write("\n");
		}
	}
	
	public void writeMethodBodyEnd() throws IOException {
		popIndentLevel();
		writeIndent();
		write("}\n");
	}
	
	protected void writeMethodBodyStartFormalArgument(ClassName className, String parameterName) throws IOException {
		writeClassName(className);
		write(" ");
		write(parameterName);
	}
	
	/**
	 * Write a class name that can be shortened
	 *  
	 * @param fqcn
	 * @throws IOException 
	 */
	public void writeClassName(ClassName fqcn) throws IOException {
		String className = getAvailableShortName(fqcn);
		write(className);
	}

	public void writeEmptyLine() throws IOException {
		if (skipNextEmptyLine) {
			skipNextEmptyLine = false;
			return;
		}

		write("\n");
	}

	public void writeJavadoc(String... lines) throws IOException {
		flushImports();
		writeEmptyLine();
		writeIndent();
		write("/**\n");
		for (String line : lines) {
			writeIndent();
			write(" * ");
			write(line);
			write("\n");
		}
		writeIndent();
		write(" */\n");
		skipNextEmptyLine = true;
	}

	public void writeAnnotation(ClassName annotation) throws IOException {
		writeAnnotation(annotation, null);
	}

	public void writeAnnotation(ClassName annotation, String parameters) throws IOException {
		writeEmptyLine();
		writeIndent();
		write("@");
		writeClassName(annotation);
		if (parameters != null) {
			write("(");
			write(parameters);
			write(")");
		}
		write("\n");
		skipNextEmptyLine = true;
	}
}