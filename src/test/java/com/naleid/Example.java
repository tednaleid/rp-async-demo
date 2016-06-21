//package com.naleid;
//
//    * import ratpack.exec.Execution;
//    * import ratpack.registry.RegistrySpec;
//    * import ratpack.rx.RxRatpack;
//    * import ratpack.test.exec.ExecHarness;
//    *
//    * import rx.Observable;
//    *
//    * import static org.junit.Assert.assertEquals;
//    *
//    * public class Example {
//    *   public static void main(String[] args) throws Exception {
//    *     RxRatpack.initialize();
//    *
//    *     try (ExecHarness execHarness = ExecHarness.harness(6)) {
//    *       String concatenatedResult = execHarness.yield(execution -> {
//    *
//    *         Observable<String> notYetForked = Observable.just("foo")
//    *             .map((value) -> value + Execution.current().get(String.class));
//    *
//    *         Observable<String> forkedObservable = RxRatpack.fork(
//    *             notYetForked,
//    *             (RegistrySpec registrySpec) -> registrySpec.add("bar")
//    *         );
//    *
//    *         return RxRatpack.promiseSingle(forkedObservable);
//    *       }).getValueOrThrow();
//    *
//    *       assertEquals(concatenatedResult, "foobar");
//    *     }
//    *   }
//    * }