
package com.fingerdev.callchain;

/**
 *
 * @author nerobot
 */

/**
 * This class is a special chunk that allow to emit data passed to it constructor.
 * If input data is array or iterable then it will be produced as
 * sequence of single items.
 * 
 * Note: this chunk ignore input data passed to it from previous chunk.
 * @param <T> items type
 */
public class Just<T> extends CallChain.Worker<T, T>{

   private T[] array;
   private Iterable<T> iterable;
   private T value;

   public Just(T param){
       value = param;
   }

   public Just(T... params){
       array = params;
   }

   public Just(Iterable<T> params){
       iterable = params;
   }
   
   @Override
   public void onReceive(T data){

       // don't use input 'data' here.

       if (!canWork())
           return;

       if (value != null){

           sendResult(value);

       } else if (array != null){

           for (T i : array){
               if (!canWork())
                   break;
               sendResult(i);
           }
       } else if (iterable != null){

           for (T i : iterable){
               if (!canWork())
                   break;
               sendResult(i);
           }
       }

       onComplete();
   }

}
