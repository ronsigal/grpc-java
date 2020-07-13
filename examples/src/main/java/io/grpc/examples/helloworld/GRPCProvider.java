package io.grpc.examples.helloworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.annotation.Priority;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.google.protobuf.Message;

@Provider
@Produces("application/grpc")
@Consumes("application/grpc")
@Priority(-1111)
public class GRPCProvider<T> implements MessageBodyReader<T>, MessageBodyWriter<T>
{  
   @Override
   public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
   {
      return true;
   }

   @Override
   public void writeTo(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
         MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
               throws IOException, WebApplicationException
   {
      Message message = null;
      try
      {
         if (t instanceof Message)
         {
            message = (Message) t;
            message.writeTo(entityStream);
         }
         else
         {
//            message = new ProtobufCompiler().compile(directory, t);
//            ??
         }
      }
      catch (Exception e)
      {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   @Override
   public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
   {
      return true;
   }

   @SuppressWarnings("unchecked")
   @Override
   public T readFrom(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType,
         MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
               throws IOException, WebApplicationException
   {
      try
      {
            Method parseFrom = type.getDeclaredMethod("parseFrom", InputStream.class);
            parseFrom.setAccessible(true);
            return (T) parseFrom.invoke(null, entityStream);
      }
      catch (Exception e)
      {
         // TODO Auto-generated catch block
         e.printStackTrace();
         throw new WebApplicationException(e);
      }
   }
}
