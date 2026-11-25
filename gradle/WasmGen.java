import com.dylibso.chicory.build.time.compiler.Config;
import com.dylibso.chicory.build.time.compiler.Generator;
import com.dylibso.chicory.compiler.InterpreterFallback;

import java.nio.file.Path;
import java.util.Set;

public class WasmGen {
    public static void main(String[] args) throws Exception {
        Path wasmFile = Path.of(args[0]);
        String className = args[1];
        Path classes = Path.of(args[2]);
        Path sources = Path.of(args[3]);

        Config config = Config.builder()
                .withWasmFile(wasmFile)
                .withName(className)
                .withTargetClassFolder(classes)
                .withTargetSourceFolder(sources)
                .withTargetWasmFolder(classes)
                .withInterpreterFallback(InterpreterFallback.FAIL)
                .build();

        Generator generator = new Generator(config);
        Set<Integer> interpreted = generator.generateResources();
        generator.generateMetaWasm(interpreted);
        generator.generateSources();
    }
}
