package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.mod.loading.ModClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Runs the classes of one built mod jar in its own {@link ModClassLoader}, as the runtime loads a mod. The mod's classes share only the
 * engine's types with the test, so the test reaches the mod's own public API by reflection: by class name, member name and the arguments,
 * whose count and types select the public method or constructor.
 */
final class ModJarScenario implements AutoCloseable {
    /**
     * Checks a result that must exist.
     *
     * @param value the result
     * @return the result
     */
    static @NotNull Object require(@Nullable Object value) {
        Assertions.assertNotNull(value, "the mod call returns a value");

        return value;
    }

    /**
     * Calls the public method (or, without a name, the public constructor) of a mod class whose parameters take the arguments. A failure of the
     * mod's code comes back as an {@link IllegalStateException} with the mod's exception as its cause.
     */
    private static @Nullable Object call(@NotNull Class<?> type, @Nullable Object target, @Nullable String name,
                                         @Nullable Object @NotNull ... arguments) throws ReflectiveOperationException {
        Executable[] candidates = name == null ? type.getConstructors() : type.getMethods();

        for (Executable candidate : candidates) {
            if ((name == null || candidate.getName().equals(name)) && accepts(candidate.getParameterTypes(), arguments)) {
                try {
                    return candidate instanceof Constructor<?> constructor ? constructor.newInstance(arguments)
                                                                           : ((Method) candidate).invoke(target, arguments);
                } catch (InvocationTargetException exception) {
                    throw Exceptions.newIllegalStateException(exception.getCause(), "{0}.{1} failed.", type.getName(), name);
                }
            }
        }

        return Assertions.fail(type.getName() + " has no public " + name + " for " + arguments.length + " arguments");
    }

    private static boolean accepts(@NotNull Class<?> @NotNull [] parameters, @Nullable Object @NotNull [] arguments) {
        if (parameters.length != arguments.length) {
            return false;
        }

        for (int index = 0; index < parameters.length; index++) {
            Object argument = arguments[index];
            Class<?> parameter = parameters[index];

            if (argument == null ? parameter.isPrimitive() : !wrap(parameter).isInstance(argument)) {
                return false;
            }
        }

        return true;
    }

    private static @NotNull Class<?> wrap(@NotNull Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        }

        if (type == boolean.class) {
            return Boolean.class;
        }

        return type;
    }


    private final @NotNull ModClassLoader loader;

    /**
     * Creates a {@link ModJarScenario} instance with a mod class loader of the jar under the test's class loader.
     *
     * @param modId the mod id, the class loader's name
     * @param jar   the built mod jar
     * @throws IOException if the jar path is not a valid URL
     */
    ModJarScenario(@NotNull String modId, @NotNull Path jar) throws IOException {
        this.loader = new ModClassLoader(modId, jar.toUri().toURL(), ModJarScenario.class.getClassLoader());
    }

    /**
     * Loads and initializes a class of the mod.
     *
     * @param className the binary class name
     * @return the mod's class of the name, initialized
     * @throws ClassNotFoundException if neither the mod nor the runtime has the class
     */
    @NotNull Class<?> loadClass(@NotNull String className) throws ClassNotFoundException {
        return Class.forName(className, true, this.loader);
    }

    /**
     * Calls the public constructor of a mod class that takes the arguments.
     *
     * @param className the binary class name
     * @param arguments the arguments
     * @return the created instance
     * @throws ReflectiveOperationException if the class cannot be loaded or the constructor cannot be called
     */
    @NotNull Object construct(@NotNull String className, @Nullable Object @NotNull ... arguments) throws ReflectiveOperationException {
        return require(call(this.loadClass(className), null, null, arguments));
    }

    /**
     * Calls the public static method of a mod class that takes the arguments.
     *
     * @param className the binary class name
     * @param name      the method name
     * @param arguments the arguments
     * @return the method's result, {@code null} for a {@code void} method
     * @throws ReflectiveOperationException if the class cannot be loaded or the method cannot be called
     */
    @Nullable Object callStatic(@NotNull String className, @NotNull String name, @Nullable Object @NotNull ... arguments) throws ReflectiveOperationException {
        return call(this.loadClass(className), null, name, arguments);
    }

    /**
     * Calls the public method of a mod object that takes the arguments.
     *
     * @param target    the mod object
     * @param name      the method name
     * @param arguments the arguments
     * @return the method's result, {@code null} for a {@code void} method
     * @throws ReflectiveOperationException if the method cannot be called
     */
    @Nullable Object invoke(@NotNull Object target, @NotNull String name, @Nullable Object @NotNull ... arguments) throws ReflectiveOperationException {
        return call(target.getClass(), target, name, arguments);
    }

    /**
     * Closes the mod class loader.
     *
     * @throws IOException if the jar cannot be closed
     */
    @Override
    public void close() throws IOException {
        this.loader.close();
    }
}
