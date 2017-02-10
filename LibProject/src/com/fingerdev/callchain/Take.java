
package com.fingerdev.callchain;

/**
 *
 * @author nerobot
 */

/**
 * This class is a special chunk that allow to take a custom amount of items and stop work after that.
 * Note, that this chunk emit onStop() method for all chunk above.
 * So that chunks that generates data can use stopped state to stop producing.
 * @param <T> items type
 */
public class Take<T> extends CallChain.Worker<T, T>{

    private int count, counter;

    /**
     * Create an instance with a given limit.
     * @param count limit value
     */
    public Take(int count){
        
        this.count = count;
    }

    /**
     * This method allow to change count after instance will be attached to chain.
     * @param count limit of items to be taken
     * @return self
     */
    public Take setCount(int count){
        
        this.count = count;
        return this;
    }

    @Override
    public void onReceive(T data){

        if (!canWork())
            return;
        
        ++counter;

        if (counter <= count)
            sendResult(data); // just send to next chunk

        if (counter >= count){
            stopBefore(); // stop all chunks above
            onComplete(); // emit completion
        }
    }

    @Override
    protected void reset(){
        
        super.reset();
        counter = 0;
    }
}
