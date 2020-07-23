/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.examples.helloworld;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;

import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class HelloWorldServerJAXRS {
   private static final Logger logger = Logger.getLogger(HelloWorldServerJAXRS.class.getName());

   private Server server;

   private static UndertowJaxrsServer undertowServer;

   /*
    * JAX-RS resource used by gRPC 
    */
   @Path("HelloWorldProto.Greeter")
   public static class TestResource {

      @POST
      @Path("sayHello")
      @Consumes("application/grpc")
      @Produces("application/grpc")
      public HelloReply sayHello(HelloRequest request) throws Exception {
         return HelloReply.newBuilder().setMessage("well hello " + request.getName()).build();
      }
   }

   @ApplicationPath("")
   public static class MyApp extends Application {
      @Override
      public Set<Class<?>> getClasses() {
         HashSet<Class<?>> classes = new HashSet<Class<?>>();
         classes.add(TestResource.class);
         classes.add(GRPCProvider.class);
         return classes;
      }
   }

   /**
    * Start gRPC server and JAX-RS server.
    */
   private void start() throws IOException {
      /* The port on which the server should run */
      //    int port = 50051;
      int port = 8081;
      server = ServerBuilder.forPort(port)
            .addService(new GreeterImpl())
            .build()
            .start();
      logger.info("Server started, listening on " + port);
      Runtime.getRuntime().addShutdownHook(new Thread() {
         @Override
         public void run() {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
               HelloWorldServerJAXRS.this.stop();
            } catch (InterruptedException e) {
               e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
         }
      });

      // Start undertow and find HttpServletDispatcher.
      undertowServer = new UndertowJaxrsServer().setPort(8082);
      undertowServer.deploy(MyApp.class);
      undertowServer.start();
      undertowServer.getDeployment().start();
      System.out.println("gRPC server ready to use JAX-RS");
   }

   private void stop() throws InterruptedException {
      if (server != null) {
         server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
      }
      if (undertowServer != null) {
         undertowServer.stop();
      }
   }

   /**
    * Await termination on the main thread since the grpc library uses daemon threads.
    */
   private void blockUntilShutdown() throws InterruptedException {
      if (server != null) {
         server.awaitTermination();
      }
   }

   /**
    * Main launches the server from the command line.
    */
   public static void main(String[] args) throws IOException, InterruptedException {
      final HelloWorldServerJAXRS server = new HelloWorldServerJAXRS();
      server.start();
      server.blockUntilShutdown();
   }

   static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

      public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
         JAXRSForwarderBuilder builder = new JAXRSForwarderBuilder();
         builder.servlet("ResteasyServlet").pathTranslator((String s) -> ("test/" + s));
         JAXRSForwarder forwarder = builder.build();
         forwarder.forward(req, (StreamObserver) responseObserver);
      }
   }
}
