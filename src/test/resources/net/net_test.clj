(ns vertx.net-test
  (:require [vertx.net :as net]
            [vertx.buffer :as buf]
            [vertx.stream :as stream]
            [vertx.testtools :as t]))

(defn assert-socket-addresses [socket]
  (t/assert-not-nil (-> socket .localAddress .getAddress))
  (t/assert (> (-> socket .localAddress .getPort) -1))
  (t/assert-not-nil (-> socket .remoteAddress .getAddress))
  (t/assert (> (-> socket .remoteAddress .getPort) -1))
  socket)

(defn echo-handler [socket]
  (stream/on-data socket
                  (fn [data]
                    (.write socket data))))

(defn exercise-handlers [socket]
  (-> socket
      (stream/on-drain #(println "drain"))
      (stream/on-end #(println "end"))
      (net/on-close #(println "close"))))

(defn test-echo []
  (letfn [(client-data-handler [sent-buf! rcv-buf! send-count send-size data]
            (buf/append! rcv-buf! data)
            (when (= (.length rcv-buf!) (* send-count send-size))
              (t/test-complete
               (t/assert= sent-buf! rcv-buf!))))
          
          (client-connect-handler [err socket]
            (assert-socket-addresses socket)
            (let [sent-buf! (buf/buffer)
                  rcv-buf! (buf/buffer)
                  send-count 10
                  send-size 100]
              (stream/on-data socket (partial client-data-handler
                                           sent-buf! rcv-buf!
                                           send-count send-size))
              (dotimes [_ send-count]
                (let [data (t/random-buffer send-size)]
                  (buf/append! sent-buf! data)
                  (.write socket data)))))
                    
          (server-listen-handler [orig-server port err server]
            (t/assert-nil err)
            (t/assert= orig-server server)
            (net/connect port client-connect-handler))]
    
    (let [server (net/server)
          port 8080]
      (-> server
          (net/on-connect (comp assert-socket-addresses
                                echo-handler
                                exercise-handlers))
          (net/listen port "localhost"
                      (partial server-listen-handler server port))))))

(defn test-echo-ssl []
  (t/test-complete))

(defn test-write-str []
  (t/test-complete))

(t/start-tests)
