package io.appform;

import java.util.concurrent.ExecutorService;

public interface ExecutorServiceProvider {

    ExecutorService threadPool(String name, int parallel, int queueSize);

}
