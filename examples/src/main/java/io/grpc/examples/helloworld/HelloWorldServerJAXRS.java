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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;

import com.google.protobuf.Message;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.core.DeploymentImpl;
import io.undertow.servlet.core.Lifecycle;
import io.undertow.servlet.core.ManagedServlet;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class HelloWorldServerJAXRS {
   private static final Logger logger = Logger.getLogger(HelloWorldServerJAXRS.class.getName());

   private Server server;
   
   private static UndertowJaxrsServer undertowServer;
   private static HttpServletDispatcher httpDispatcher;

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
      Collection<String> c = undertowServer.getManager().getDeployment().getServletContainer().listDeployments();
      for (String s: c) {
         DeploymentImpl d = (DeploymentImpl) undertowServer.getManager().getDeployment();
         List<Lifecycle> list = d.getLifecycleObjects();
         for (Lifecycle l : list) {
//            System.out.println(l);
            if (l instanceof ManagedServlet) {
               ManagedServlet ms = (ManagedServlet) l;
               InstanceHandle<?> handle;
               try
               {
                  handle = ms.getServlet();
//                  System.out.println(handle);
                  Object o = handle.getInstance();
//                  System.out.println(o);
                  if (o instanceof HttpServletDispatcher) {
                     httpDispatcher = (HttpServletDispatcher) o;
                  }
               }
               catch (ServletException e)
               {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
               }
            }
         }
      }
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

      @Override
      /*
       * Pass invocation to HttpServletDispatcher in JAX-RS
       */
      public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
         try {
            HttpServletResponse response = getHttpServletResponse();
            httpDispatcher.service(getHttpServletRequest(req), response);
            MockServletOutputStream msos = (MockServletOutputStream) response.getOutputStream();
            ByteArrayOutputStream baos = msos.getDelegate();
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            HelloReply helloReply = HelloReply.parseFrom(bais);
            responseObserver.onNext(helloReply);
         } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
            return;
         }
         responseObserver.onCompleted();
      }
   }

   /**
    * Generate HttpServletRequest proxy
    */
   static HttpServletRequest getHttpServletRequest(Message message)
   {
      return (HttpServletRequest) Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(), 
            new Class[] { HttpServletRequest.class }, 
            new HttpServletRequestHandler("HelloWorldProto.Greeter/sayHello", message));
   }

   /**
    * Handler for HttpServletRequest proxy
    */
   static  class HttpServletRequestHandler implements InvocationHandler {
      private String path;
      private Message message;
      private Map<String, Object> attributes = new HashMap<String, Object>();
      
      public HttpServletRequestHandler(String path, Message message) {
         this.path = path;
         this.message = message;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("getMethod".equals(method.getName())) {
           return "POST";
        }
        if ("getHeaderNames".equals(method.getName())) {
           return Collections.enumeration(new ArrayList<String>());
        }
        if ("getContentType".equals(method.getName())) {
           return "application/grpc";
        }
        if ("getRequestURL".equals(method.getName())) {
           return new StringBuffer("http://localhost:8081/" + path);
        }
        if ("getInputStream".equals(method.getName())) {
           ByteArrayOutputStream baos = new ByteArrayOutputStream();
           message.writeTo(baos);
           return new MockServletInputStream(new ByteArrayInputStream(baos.toByteArray()));
        }
        if ("setAttribute".equals(method.getName())) {
           attributes.put((String) args[0], args[1]);
           return null;
        }
        if ("getAttribute".equals(method.getName())) {
           return attributes.get(args[0]);
        }
        return null;
      }
   }
   
   /**
    * Generate HttpServletResponse proxy.
    */
   static HttpServletResponse getHttpServletResponse()
   {
      return (HttpServletResponse) Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(), 
            new Class[] { HttpServletResponse.class }, 
            new HttpServletResponseHandler());
   }

   /**
    * Handler for HttpServletResponse proxy
    */
   static  class HttpServletResponseHandler implements InvocationHandler {
      private MockServletOutputStream msos = new MockServletOutputStream();
      
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         if ("isCommitted".equals(method.getName())) {
            return true;
         }
         if ("getOutputStream".equals(method.getName())) {
            return msos;
         }
         return null;
      }
   }
   
   /**
    * ServletInputStream for HttpServletRequest proxy.
    */
   static class MockServletInputStream extends ServletInputStream {
      private InputStream is;
      
      public MockServletInputStream(InputStream is) {
         this.is = is;
      }
      
      @Override
      public boolean isFinished()
      {
         try
         {
            return is.available() > 0;
         }
         catch (IOException e)
         {
            return true;
         }
      }

      @Override
      public boolean isReady()
      {
         return true;
      }

      @Override
      public void setReadListener(ReadListener readListener)
      { 
      }

      @Override
      public int read() throws IOException
      {
         return is.read();
      }
   }
   
   /**
    * ServletOutputStream for HttpServletResponse proxy
    */
   static class MockServletOutputStream extends ServletOutputStream {
      private ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      @Override
      public boolean isReady()
      {
         return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener)
      {
      }

      @Override
      public void write(int b) throws IOException
      {
         baos.write(b);
      }
      
      public ByteArrayOutputStream getDelegate() {
         return baos;
      }
   }
}
