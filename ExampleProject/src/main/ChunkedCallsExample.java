
package main;

import com.fingerdev.callchain.CallChain;

/**
 *
 * @author nerobot
 */
public class ChunkedCallsExample {

    
    public static void main(String[] args) {

        
        /* Receiver. End point for our chain */

        CallChain.Receiver receiver = new CallChain.Receiver<String>() {
            @Override
            public void onReceive(String data) {
                System.out.println("#receive: "+data);
            }

            @Override
            public void onError(Throwable err) {
                System.out.println("#error: "+err.getMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("#complete");
            }
        };

                
        /* Start our chained process */

        CallChain chain = new CallChain();
        chain
                // producing sequence of Integers
                .just( getNumberArray(-2,10) )
                
                // convert array to single items
                .<Integer>flatMap()
                
                // calc sum of integers. return (value+value)
                .map(new CallChain.ICaller<Integer,Integer>() {
                    @Override
                    public Integer call(Integer data) {
                        return data+data;
                    }
                })
                
                // filter negative values
                .filter(new CallChain.ICaller<Integer,Boolean>() {
                    @Override
                    public Boolean call(Integer data) {
                        return data >= 0;
                    }
                })
                
                // append prefix "item " to a number, so that convert value to string
                .map(new CallChain.ICaller<Integer,String>() {
                    @Override
                    public String call(Integer data) {
                        return "item "+data;
                    }
                })
                
                // let's process data before uppercasing
                .catchData(new CallChain.ICallerIn<String>() {
                    @Override
                    public void call(String data) {
                        //socket.send(data);
                        System.out.println("send to socket: "+data);
                    }
                })
                
                // uppercase incoming string
                .map(new CallChain.ICaller<String,String>() {
                    @Override
                    public String call(String data) {
                        return data.toUpperCase();
                    }
                })
                
                // try to replace "100" with "777"
                .map(new CallChain.ICaller<String,String>() {
                    @Override
                    public String call(String data) {
                        return data.replace("100", "777");
                    }
                })
                
                // take only first 5 items
                .take(5)
                
                // catch onComplete() event
                .catchComplete(new CallChain.ICallerVoid() {
                    @Override
                    public void call() {
                        System.out.println("we are done!");
                    }
                })
                
                // do something with result. (print to system.out here)
                .add(receiver);
        
        chain.run();
        
    }
    
    private static Integer[] getNumberArray(int start, int count){

        Integer[] arr = new Integer[count];
        int sum = 0, index = 0;
        for (int k = start; k < start+count; ++k){
            sum += k;
            arr[index++] = sum;
        }
        return arr;
    }

}
