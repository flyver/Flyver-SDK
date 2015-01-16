package co.flyver.utils.flyverMQ;

import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.flyver.IPC.IPCContainers;
import co.flyver.IPC.JSONUtils;
import co.flyver.utils.flyverMQ.exceptions.NoSuchTopicException;
import co.flyver.utils.flyverMQ.exceptions.ProducerAlreadyRegisteredException;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQConsumer;

import static co.flyver.IPC.IPCContainers.JSONHextuple;

/**
 * Created by Petar Petrov on 1/7/15.
 */

/**
 * Worker used to associate externally defined producers or consumers
 * with the message queue over raw sockets.
 */
public class FlyverMQExternalWorker implements Runnable {

    /**
     * Wraps the client's connection with a SimpleMQConsumer interface
     * and sends the data received on it to the external consumer
     */
    private class FlyverMQSocketClientConsumer implements FlyverMQConsumer {
        Socket socket;
        PrintWriter printWriter;

        public FlyverMQSocketClientConsumer(Socket socket) {
            this.socket = socket;
            try {
                printWriter = new PrintWriter(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void dataReceived(FlyverMQMessage message) {
            String msg;
            msg = JSONUtils.serialize(message);
            printWriter.println(msg.concat("\n"));
        }

        @Override
        public void unregistered() {

        }

        @Override
        public void paused() {

        }

        @Override
        public void resumed() {

        }
    }

    /**
     * Wraps the external client in a SimpleMQProducer class,
     * so messages passed as JSON via sockets can be added to the message queue
     */
    private class FlyverMQSocketClientProducer extends FlyverMQProducer {
        Socket socket;
        BufferedReader reader;
        Thread thread;
        JSONHextuple<Long, Short, Long, String, Long, Object> hextuple;
        Type hextupleType = new TypeToken<JSONHextuple<Long, Short, Long, String, Long, Object>>() {}.getType();
        FlyverMQMessage message;
        FlyverMQSocketClientProducer ref;
        FlyverMQ mqRef;

        private FlyverMQSocketClientProducer(FlyverMQ mq, Socket socket) {
            this.socket = socket;
            this.mqRef = mq;
            //get reference of this object instance, so it can be used in inner classes
            ref = this;
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //noinspection InfiniteLoopStatement
                    while (true) {
                        String line = null;
                        try {
                            line = reader.readLine();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        hextuple = JSONUtils.deserialize(line, hextupleType);
                        message = new FlyverMQMessage.MessageBuilder().setTtl(hextuple.getValue1()).
                                setPriority(hextuple.getValue2()).setMessageId(hextuple.getValue3()).
                                setTopic(hextuple.getValue4()).setCreationTime(hextuple.getValue5()).setData(hextuple.getValue6()).build();
                        try {
                            mqRef.addMessage(ref, message);
                        } catch (NoSuchTopicException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        @Override
        public void registered() {

        }

        @Override
        public void unregistered() {

        }

        @Override
        public void onPause() {

        }

        @Override
        public void onResume() {

        }
    }

    private FlyverMQ flyverMQ;
    private Socket client;
    private IPCContainers.JSONTriple<String, String, String> json;
    private Type jsonType = new TypeToken<IPCContainers.JSONTriple<String, String, String>>() {
    }.getType();

    public FlyverMQExternalWorker(FlyverMQ flyverMQ, Socket client) {
        this.flyverMQ = flyverMQ;
        this.client = client;
    }

    @Override
    public void run() {
        String line;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //noinspection InfiniteLoopStatement
//        if (reader != null) {
//            try {
//                line = reader.readLine();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        try {
            if (reader != null) {
                while ((line = reader.readLine()) != null) {
                    //get the value of the key "key"
                    Pattern mPattern = Pattern.compile("\\{.+(?=key)\\w+\":(\"\\w+\")\\}?.+\\}?$");
                    Matcher matcher = mPattern.matcher(line);
                    if (JSONUtils.validateJson(line) && matcher.matches()) {
                        json = JSONUtils.deserialize(line, jsonType);
                        switch (json.getValue().toLowerCase()) {
                            //{"key":"mq", "value":"consumer", "action":"sensors.raw"}
                            case "consumer": {
                                FlyverMQSocketClientConsumer socketClient = new FlyverMQSocketClientConsumer(client);
                                flyverMQ.registerConsumer(socketClient, json.getAction());
                            }
                            break;
                            //{"key":"mq", "value":"producer", "action":"sensors.raw"}
                            case "producer": {
                                FlyverMQSocketClientProducer socketClientProducer = new FlyverMQSocketClientProducer(flyverMQ, client);
                                try {
                                    flyverMQ.registerProducer(socketClientProducer, json.getAction(), false);
                                    //TODO:: Implement catch blocks to notify client module that registration was unsuccessful
                                } catch (ProducerAlreadyRegisteredException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                            //{"key":"mq", "value":"producers", "action":null}
                            //TODO:: add filtering of the requested topics by string/regex
                            case "producers": {
                                try {
                                    PrintWriter writer = new PrintWriter(client.getOutputStream());
                                    Object response;
                                    response = FlyverMQCtl.getInstance().listAllTopics();
                                    String json = JSONUtils.serialize(response);
                                    writer.println(json);
                                    writer.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                            default: {
                                //TODO:: implement Default
                            }
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
