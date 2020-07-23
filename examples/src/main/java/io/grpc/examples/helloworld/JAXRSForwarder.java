package io.grpc.examples.helloworld;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ReadListener;
import javax.servlet.Servlet;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.resteasy.core.ResteasyContext;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;

import io.grpc.stub.StreamObserver;

public class JAXRSForwarder {

   public void forward(GeneratedMessageV3 request, StreamObserver<GeneratedMessageV3> responseObserver)
   {
      try {
         HttpServletResponse response = getHttpServletResponse();
         Servlet servlet = ResteasyContext.getServlet("ResteasyServlet");
         servlet.service(getHttpServletRequest(request), response);
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
