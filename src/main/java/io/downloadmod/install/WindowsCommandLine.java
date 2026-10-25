package io.downloadmod.install;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class WindowsCommandLine {
	private WindowsCommandLine() {
	}

	static Optional<List<String>> read() {
		try {
			ClassLoader cl = WindowsCommandLine.class.getClassLoader();
			Class<?> fn = Class.forName("com.sun.jna.Function", true, cl);
			Class<?> pointer = Class.forName("com.sun.jna.Pointer", true, cl);
			Class<?> wstring = Class.forName("com.sun.jna.WString", true, cl);
			Class<?> intRef = Class.forName("com.sun.jna.ptr.IntByReference", true, cl);
			Class<?> nativeCls = Class.forName("com.sun.jna.Native", true, cl);

			Method getFunction = fn.getMethod("getFunction", String.class, String.class);
			Method invokePointer = fn.getMethod("invokePointer", Object[].class);
			Method getWideString = pointer.getMethod("getWideString", long.class);
			Method getPointer = pointer.getMethod("getPointer", long.class);

			Object getCmd = getFunction.invoke(null, "kernel32", "GetCommandLineW");
			Object cmdPtr = invokePointer.invoke(getCmd, (Object) new Object[0]);
			String commandLine = (String) getWideString.invoke(cmdPtr, 0L);

			Object count = intRef.getConstructor().newInstance();
			Object toArgv = getFunction.invoke(null, "shell32", "CommandLineToArgvW");
			Object argv = invokePointer.invoke(toArgv, (Object) new Object[]{wstring.getConstructor(String.class).newInstance(commandLine), count});
			int n = (int) intRef.getMethod("getValue").invoke(count);
			int pointerSize = (int) nativeCls.getField("POINTER_SIZE").get(null);

			List<String> out = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				Object p = getPointer.invoke(argv, (long) i * pointerSize);
				out.add((String) getWideString.invoke(p, 0L));
			}
			return out.isEmpty() ? Optional.empty() : Optional.of(out);
		} catch (Throwable t) {
			return Optional.empty();
		}
	}
}
