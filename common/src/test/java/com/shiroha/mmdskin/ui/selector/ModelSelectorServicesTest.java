package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimePort;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultMaterialVisibilityGateway;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultModelSettingsRuntimeGateway;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.ModelSettingsApplicationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertSame;

class ModelSelectorServicesTest {

    @Test
    void shouldResolveModelSettingsRuntimePortLazily() throws Exception {
        NativeRuntimePort original = NativeRuntimeBridgeHolder.get();
        NativeRuntimePort first = runtimePortProxy();
        NativeRuntimePort second = runtimePortProxy();
        try {
            NativeRuntimeBridgeHolder.set(first);
            ModelSettingsApplicationService service = ModelSelectorServices.modelSettings();
            DefaultModelSettingsRuntimeGateway gateway =
                    (DefaultModelSettingsRuntimeGateway) readField(service, "runtimeGateway");
            @SuppressWarnings("unchecked")
            Supplier<?> supplier = (Supplier<?>) readField(gateway, "nativeScenePortSupplier");

            NativeRuntimeBridgeHolder.set(second);

            assertSame(second, supplier.get());
        } finally {
            NativeRuntimeBridgeHolder.set(original);
        }
    }

    @Test
    void shouldResolveMaterialVisibilityRuntimePortsLazily() throws Exception {
        NativeRuntimePort original = NativeRuntimeBridgeHolder.get();
        NativeRuntimePort first = runtimePortProxy();
        NativeRuntimePort second = runtimePortProxy();
        try {
            NativeRuntimeBridgeHolder.set(first);
            MaterialVisibilityApplicationService service = ModelSelectorServices.materialVisibility();
            DefaultMaterialVisibilityGateway gateway =
                    (DefaultMaterialVisibilityGateway) readField(service, "gateway");
            @SuppressWarnings("unchecked")
            Supplier<?> modelPortSupplier = (Supplier<?>) readField(gateway, "nativeModelPortSupplier");
            @SuppressWarnings("unchecked")
            Supplier<?> queryPortSupplier = (Supplier<?>) readField(gateway, "nativeModelQueryPortSupplier");

            NativeRuntimeBridgeHolder.set(second);

            assertSame(second, modelPortSupplier.get());
            assertSame(second, queryPortSupplier.get());
        } finally {
            NativeRuntimeBridgeHolder.set(original);
        }
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static NativeRuntimePort runtimePortProxy() {
        return (NativeRuntimePort) Proxy.newProxyInstance(
                ModelSelectorServicesTest.class.getClassLoader(),
                new Class<?>[]{NativeRuntimePort.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "NativeRuntimePortProxy";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
