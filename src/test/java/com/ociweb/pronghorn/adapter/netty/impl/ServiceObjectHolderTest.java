package com.ociweb.pronghorn.adapter.netty.impl;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

public class ServiceObjectHolderTest {

    int iterations = 100000;
    
    private boolean expireRule(String serviceObject) {
        return serviceObject.charAt(0)=='1';
    }

    @Test
    public void lookup() {
        
     ServiceObjectValidator<String> validator = new ServiceObjectValidator<String>() {

        @Override
        public String isValid(String serviceObject) {
            
            if (expireRule(serviceObject)) {
                return null;
            } else {
                return serviceObject;                
            }
            
            
        }

         
     };
       
     ServiceObjectHolder<String> holder = new ServiceObjectHolder<String>(String.class, validator);
     
     Random r = new Random(42);
     
     int i = iterations;
     while (--i>=0) {
         String value = Long.toHexString(r.nextLong());
         
         long key = holder.add(value);
         
         String value2 = holder.getValid(key);
         
         if (expireRule(value)) {
             assertNull(value2);
         } else {
             assertEquals(value,value2);
         }
     }
             
             
    }
    
}
