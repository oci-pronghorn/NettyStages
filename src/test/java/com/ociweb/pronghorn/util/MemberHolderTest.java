package com.ociweb.pronghorn.util;

import java.util.Random;

import org.junit.Test;
import static org.junit.Assert.*;


public class MemberHolderTest {

    public final int iterations = 20000000;
    
    @Test
    public void testHolder() {
        
       
        MemberHolder holder = new MemberHolder(10);
        
        final int listId = 3;
        final int seed = 42;

        Random r = new Random(seed);
        
        int i = 0;
        int limit =iterations;
        while (++i<=limit) {        
            if (i<100000) { //force testing of small values
                holder.addMember(listId, r.nextInt(i));
            } else {
                holder.addMember(listId, Math.abs(r.nextLong())); //TODO: we have a bug where this does not work for very large negative longs, FIX.
            }
        }
        
        final Random expectedR = new Random(seed);
        
        holder.visit(listId, new MemberHolderVisitor() {
            int i = 0;
            @Override
            public void visit(long value) { 

                if (++i<100000) {
                    assertEquals(expectedR.nextInt(i), value);
                } else {
                    assertEquals(Math.abs(expectedR.nextLong()), value);
                }
            }
            
        });
        
        
    }
    
}
