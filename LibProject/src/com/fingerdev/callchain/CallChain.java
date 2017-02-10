
package com.fingerdev.callchain;


/**
 * @author nerobot
 */

/**
 * This class allow us to construct chain of methods.
 * 
 * Output of one chunk passed as an input of the next, until onComplete() or onError() events.
 * 
 * Usualy, the first chunk is a Worker that produced data,
 * and the last chunk is a Receiver that process result data and react on complete and error events.
 * 
 * There are special chunks called 'operators' that help us to build more clear chain.
 * (flatMap, map, take, filter)
 */
public class CallChain {
    
    private static final int STATE_WORK = 0;
    private static final int STATE_COMPLETE = 1;
    private static final int STATE_ERROR = 2;
    private static final int STATE_STOP = 4;
    
    
    //--------- ICaller -------------------------------------------------------------
    
    /**
     * Contains the only method to do some work.
     * Get input data and return output data.
     * @param <TIn> input type
     * @param <TOut> output type
     */
    public static interface ICaller<TIn,TOut>{
        
        public abstract TOut call(TIn data);
        
    }
    
    /**
     * Contains the only method to do some work.
     * Get input data only.
     * @param <TIn> input type
     */
    public static interface ICallerIn<TIn>{
        
        public abstract void call(TIn data);
        
    }
    
    /**
     * Contains the only method to do some work.
     * Don't get / return anything.
     */
    public static interface ICallerVoid{
        
        public abstract void call();
        
    }
    
    //--------- Chunk -------------------------------------------------------------
    
    /**
     * Chunk present logical chain part.
     * @param <TIn> input type, that this chunk received
     * @param <TOut> output type, that this chunk send to the next chunk
     */
    public static abstract class Chunk<TIn,TOut> implements ICaller<TIn, TOut>{
        
        /**
         * Override this method to do all work inside.
         * @param data input data
         * @return some result data
         */
        @Override
        public abstract TOut call(TIn data);
        
        private Chunk nextChunk;        // next chunk that use this output as its input
        private int state = -1;         // -1 - unknown state
        private CallChain parent;       // chain reference

        /**
         * This method process input data by calling call() method of this chunk
         * and pass result to onReceive() method of next chunk if it exists.
         * @param data input value got from previous chunk or passed into callchain.run() method
         */
        public void onReceive(TIn data){

            if (!canWork())
                return;
            
            TOut result = call(data);
            
            sendResult(result);
        }
        
        /**
         * This method called if error occured.
         * This event go throught all chunks placed after the chunk emited onError().
         * Authors of chunks that producing data must care about emitting this event.
         * @param err instance of error
         */
        public void onError(Throwable err){
            
            if (state != STATE_WORK)
                return;
            state = STATE_ERROR;
            if (nextChunk != null)
                nextChunk.onError(err);
        }
        
        /**
         * This method mean that work complete.
         * This event go throught all chunks placed after the chunk emited onComplete().
         * Authors of chunks that producing data must care about emitting this event.
         */
        public void onComplete(){
            
            if (state != STATE_WORK)
                return;
            state = STATE_COMPLETE;
            if (nextChunk != null)
                nextChunk.onComplete();
        }

        /**
         * This method goal is to stop producing data.
         * This event go throught all chunks placed *before* the chunk emited onStop().
         * Authors of chunks that producing data must care about stopping by checking canWork() method.
         */
        protected void stopBefore(){
            
            parent.stopBefore(this);
        }

        /**
         * Is this chunk "alive" to produce data.
         * @return true if alive
         */
        public boolean canWork(){
            
            return state == STATE_WORK;
        }

        /**
         * Use this method inside of overriden call() method to pass data to the next chunk.
         * This method do check 'nextChunk != null AND canWork()'.
         * @param data value to send
         */
        protected void sendResult(TOut data){
            
            if (nextChunk != null && canWork()){
                nextChunk.onReceive(data);
            }
        }
        
        /**
         * Reset state and so on.
         * This method called in CallChain.run() for all chunks before running first of them.
         */
        protected void reset(){
            
            state = STATE_WORK;
        }
        
        /**
         * We must assign parent for all chunks to use onStop().
         * @param chunk next chunk reference
         */
        private void setNextChunk(Chunk chunk){
            
            chunk.parent = parent;
            nextChunk = chunk;
        }
        
        //--------- Operators -----------------------------------------------------
        
        /**
         * This method add next chunk to this one.
         * @param <TOut2> returned type of added chunk
         * @param chunk next one
         * @return added chunk as-is
         */
        public <TOut2> Chunk<TOut,TOut2> add(Chunk<TOut,TOut2> chunk){
            
            setNextChunk(chunk);
            return chunk;
        }
        
        /**
         * This operator allow to create converters:
         * * from one type to another,
         * * or just modify the input data.
         * @param <TOut2> returned type
         * @param converter contains overriden call() method that do some work.
         * @return new chunk that use converter.call() inside of this.call()
         */
        public <TOut2> Chunk<TOut,TOut2> map(final ICaller<TOut,TOut2> converter){
            
            Chunk chunk = new Chunk<TOut, TOut2>() {
                @Override
                public TOut2 call(TOut data) {
                    return converter.call(data);
                }
            };
            setNextChunk(chunk);
            return chunk;
        }

        /**
         * This operator used for filtering, it retranslate data only if filter.call() return true.
         * @param filter contains overriden call() method with filtering logic.
         * @return new chunk instance
         */
        public Chunk<TOut,TOut> filter(final ICaller<TOut,Boolean> filter){
            
            Chunk chunk = new Worker<TOut, TOut>() {
                @Override
                public void onReceive(TOut data) {
                    if (filter.call(data))
                        sendResult(data);
                }
            };
            setNextChunk(chunk);
            return chunk;
        }
        
        /**
         * This operator convert array-data (T[]) or iterable-data (Iterable&lt;T&gt;) into single items sequence.
         * Also can retranslate single items (not recommended).
         * This method using instanceof and type casting.
         * @param <TOut2> type of items
         * @return new chunk that produce sequence of single items
         */
        public <TOut2> Chunk<TOut, TOut2> flatMap(){

            Chunk chunk = new Worker<TOut, TOut2>() {
                @Override
                public void onReceive(TOut data) {

                    if (data == null){
                        onError(new NullPointerException("#flatMap got null data!"));
                        return;
                    }

                    // got an array
                    if (data instanceof Object[]){
                        
                        for (Object i : (Object[])data){
                            if (!canWork())
                                break;
                            sendResult( (TOut2)i );
                        }
                    } else
                    // got an iterable
                    if (data instanceof Iterable){
                        
                        for (Object i : (Iterable<Object>)data){
                            if (!canWork())
                                break;
                            sendResult( (TOut2)i );
                        }
                    } else {
                    // got a single value
                        sendResult( (TOut2)data );
                        //onError(new Throwable("#flatMap got a single value!"));
                    }
                }
            };
            setNextChunk(chunk);
            return chunk;
        }

        /**
         * This operator convert array-data (TOut[]) into single items sequence.
         * @param <TOut2> single items type, TOut type should be castable to this one
         * @return new chunk instance
         */
        public <TOut2> Chunk<TOut[], TOut2> flatArray(){
            
            Chunk chunk = new Worker<TOut[], TOut2>() {
                @Override
                public void onReceive(TOut[] data) {

                    if (data == null){
                        onError(new NullPointerException("#flatArray got null object instead of valid array!"));
                        return;
                    }
                    
                    for (TOut i : data){
                        if (!canWork())
                            break;
                        sendResult( (TOut2)i ); // is this casting so bad? cast to the same type here.
                    }
                }
            };
            setNextChunk(chunk);
            return chunk;
        }

        /**
         * This operator convert iterable-data (Iterable&lt;TOut&gt;) into single items sequence.
         * @param <TOut2> single items type, TOut type should be castable to this one
         * @return new chunk instance
         */
        public <TOut2> Chunk<Iterable<TOut>,TOut2> flatIterable(){
            
            Chunk chunk = new Worker<Iterable<TOut>, TOut2>() {
                @Override
                public void onReceive(Iterable<TOut> data) {

                    if (data == null){
                        onError(new NullPointerException("#flatIterable got null object instead of valid iterable!"));
                        return;
                    }

                    for (TOut i : data){
                        if (!canWork())
                            break;
                        sendResult( (TOut2)i ); // is this casting so bad? cast to the same type here.
                    }
                }
            };
            setNextChunk(chunk);
            return chunk;
        }
        
        /**
         * This operator take only N items and stop working.
         * After retranslating 'count' items it stop all chunks before it and emit onComplete().
         * @param count received items limit
         * @return new chunk instance
         */
        public Chunk<TOut,TOut> take(int count){
            
            Chunk chunk = new Take<>(count);
            setNextChunk(chunk);
            return chunk;
        }

        /**
         * This operator call catcher.call() method inside of onReceive() and then send data to the next chunk.
         * @param catcher who will do something
         * @return new chunk instance
         */
        public Chunk<Iterable<TOut>,TOut> catchData(final ICallerIn<TOut> catcher){
            
            Chunk chunk = new Worker<TOut, TOut>() {
                @Override
                public void onReceive(TOut data) {
                    catcher.call(data);
                    sendResult(data);
                }
            };
            setNextChunk(chunk);
            return chunk;
        }
        
        /**
         * This operator call catcher.call() method inside of onError() and then send error to the next chunk.
         * @param catcher who will do something
         * @return new chunk instance
         */
        public Chunk<Iterable<TOut>,TOut> catchError(final ICallerIn<Throwable> catcher){
            
            Chunk chunk = new Chunk<TOut, TOut>() {
                @Override
                public void onError(Throwable t) {
                    catcher.call(t);
                    super.onError(t);
                }

                @Override
                public TOut call(TOut data) {
                    return data; // do nothing
                }
            };
            setNextChunk(chunk);
            return chunk;
        }
        
        /**
         * This operator call catcher.call() method inside of onComplete() and then call onComplete() of the next chunk.
         * @param catcher who will do something
         * @return new chunk instance
         */
        public Chunk<Iterable<TOut>,TOut> catchComplete(final ICallerVoid catcher){
            
            Chunk chunk = new Chunk<TOut, TOut>() {
                @Override
                public void onError(Throwable t) {
                    catcher.call();
                    super.onComplete();
                }

                @Override
                public TOut call(TOut data) {
                    return data; // do nothing
                }
            };
            setNextChunk(chunk);
            return chunk;
        }
    }
    
    //--------- Receiver ----------------------------------------------------------
    
    /**
     * Optional but recommended class designed to process result of all chunks calls.
     * A receiver is an end point for chain.
     * @param <TIn> type of input items
     */
    public static abstract class Receiver<TIn> extends Chunk<TIn, Void>{
        
        @Override
        public final Void call(TIn data){
            return null; // don't use
        }
        
        @Override
        public Chunk add(Chunk chunk){
            
            throw new UnsupportedOperationException("You can't add chunks to receiver!");
        }
        
        // make these callbacks below abstract, so user must implement them
        
        @Override
        public abstract void onReceive(TIn data);
        
        @Override
        public abstract void onError(Throwable err);
        
        @Override
        public abstract void onComplete();
    }
    
    //--------- Worker ------------------------------------------------------------
    
    /**
     * Optional but recommended class designed to use as data producer.
     * @param <TIn> input type
     * @param <TOut> output type
     */
    public static abstract class Worker<TIn,TOut> extends Chunk<TIn, TOut>{
        
        /**
         * Use this method to do work: emit result(s), error and complete here.
         */ 
        
        @Override
        public abstract void onReceive(TIn data);
        
        @Override
        public final TOut call(TIn data){
            return null; // don't use
        }
        
    }
    
    //--------- CallChain ---------------------------------------------------------
    
    private Chunk first;
    
    /**
     * Add given chunk to the last chunk if any or set it as the first if there is no chunks yet.
     * @param <TIn> input type
     * @param <TOut> output type
     * @param chunk chain element to add
     * @return added part as-is
     */
    public <TIn,TOut> Chunk<TIn,TOut> add(Chunk<TIn,TOut> chunk){

        chunk.parent = this;
        if (first == null){
            first = chunk;
        } else {
            Chunk p = first, last = null;
            while (p != null){
                last = p;
                p = p.nextChunk;
            }
            last.nextChunk = chunk;
        }
        return chunk;
    }
    
    /**
     * Remove given chunk.
     * @param chunk to be removed
     * @return self
     */
    public CallChain remove(Chunk chunk){
        
        Chunk p = first;
        if (chunk == first){
            first = first.nextChunk;
            p.nextChunk = null;
            return this;
        }
        
        while (p != null){
            if (p.nextChunk == chunk){
                Chunk p2 = chunk.nextChunk;
                chunk.nextChunk = null;
                p.nextChunk = p2;
                break;
            }
            p = p.nextChunk;
        }
        
        return this;
    }
    
    /**
     * Mark all chunks before given one as stopped, but not emit onStop.
     * @param chunk stops all before this chunk
     * @return self
     */
    public CallChain stopBefore(Chunk chunk){

        Chunk p = first;
        while (p != null && p != chunk){
            p.state = STATE_STOP; // mark as stopped
            p = p.nextChunk;
        }

        return this;
    }

    /**
     * Start work passing 'input' value into the first chunk.
     * 
     * Note: if chain started with 'just' operator - it will not use this input data.
     * @param <TIn> input data type
     * @param input source data to be processed
     * @return self
     */
    public <TIn> CallChain run(TIn input){
        
        if (first == null)
            throw new UnsupportedOperationException("There must be at least one chunk to execute!");

        prepare();

        first.onReceive(input);
        
        return this;
    }
    
    /**
     * Start work passing null value into the first chunk.
     * @return self
     */
    public CallChain run(){
        
        return run(null);
    }
    
    /**
     * Start work by attaching first chunk to given worker chunk and passing 'input' value into the worker.
     * 
     * Note: if chain started with 'just' operator - it will not use output data from this worker.
     * @param <TIn> worker input type
     * @param <TOut> worket output type
     * @param worker special chunk that produced data, like Just.
     * @param input data to pass into worker
     * @return self
     */
    public <TIn,TOut> CallChain runWorker(Chunk<TIn,TOut> worker, TIn input){
        
        prepare();
        
        worker.nextChunk = first;
        worker.onReceive(input);
        return this;
    }
    
    /**
     * Add special worker that will send given value to the next chunk and then emit onComplete().
     * @param <TIn> item type
     * @param value value to be send
     * @return new chunk instance
     */
    public <TIn> Chunk<TIn,TIn> just(TIn value){
        
        Worker<TIn,TIn> chunk = new Worker<TIn, TIn>() {
            @Override
            public void onReceive(TIn data) {
                if (canWork()){
                    sendResult(data);
                    onComplete();
                }
            }
        };
        return add(chunk);
    }

    /**
     * Transform given array data into a sequence of single items and pass them to the next chunk.
     * @param <TIn> item type
     * @param value value to be splitted and send
     * @return new chunk instance
     */
    public <TIn> Chunk<TIn,TIn> just(final TIn... value){
        
        Worker<TIn,TIn> chunk = new Worker<TIn, TIn>() {
            @Override
            public void onReceive(TIn data) {
                // don't use input 'data' here
                
                for (TIn i : value){
                    if (!canWork()){
                        return;
                    }
                    sendResult(i);
                }
                onComplete();
            }
        };
        return add(chunk);
    }

    /**
     * Transform given iterable data into a sequence of single items and pass them to the next chunk.
     * @param <TIn> item type
     * @param value value to be splitted and send
     * @return new chunk instance
     */
    public <TIn> Chunk<TIn,TIn> just(final Iterable<TIn> value){
        
        Worker<TIn,TIn> chunk = new Worker<TIn, TIn>() {
            @Override
            public void onReceive(TIn data) {
                // don't use input 'data' here
                
                for (TIn i : value){
                    if (!canWork())
                        return;
                    sendResult(i);
                }
                onComplete();
            }
        };
        return add(chunk);
    }
    
    /**
     * Called before passing input data into the first chunk.
     */
    private void prepare(){
        
        int lastState = -1;
        Chunk p = first;
        while (p != null){
            lastState = p.state;
            p.reset(); // reset complete/error/stop
            p = p.nextChunk;
        }
        // check running state
        if (lastState == STATE_WORK){
            throw new UnsupportedOperationException("Can't run chain while it's in progress!");
        }
    }
    
}
