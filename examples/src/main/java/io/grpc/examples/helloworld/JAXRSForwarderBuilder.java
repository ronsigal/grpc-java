package io.grpc.examples.helloworld;

import java.util.function.Function;

public class JAXRSForwarderBuilder {
   
   public JAXRSForwarderBuilder servlet(String servlet)
   {
      return this;
   }
   
   public JAXRSForwarderBuilder pathTranslator(Function<String, String> f)
   {
      return this;
   }

   public JAXRSForwarder build()
   {
      return new JAXRSForwarder();
   }
}
