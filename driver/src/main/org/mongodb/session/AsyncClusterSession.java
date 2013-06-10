/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.session;

import org.mongodb.MongoException;
import org.mongodb.MongoInternalException;
import org.mongodb.connection.AsyncServerConnection;
import org.mongodb.connection.Cluster;
import org.mongodb.connection.Server;
import org.mongodb.connection.ServerSelector;
import org.mongodb.operation.MongoFuture;
import org.mongodb.operation.SingleResultFuture;

import java.util.concurrent.Executor;

import static org.mongodb.assertions.Assertions.isTrue;

public class AsyncClusterSession implements AsyncServerSelectingSession {

    private final Cluster cluster;
    private final Executor executor;
    private volatile boolean isClosed;

    public AsyncClusterSession(final Cluster cluster, final Executor executor) {
        this.cluster = cluster;
        this.executor = executor;
    }


    @Override
    public MongoFuture<AsyncServerConnection> getConnection(final ServerSelector serverSelector) {
        isTrue("open", !isClosed());

        final SingleResultFuture<AsyncServerConnection> retVal = new SingleResultFuture<AsyncServerConnection>();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                AsyncServerConnection connection = null;
                MongoException exception = null;
                try {
                    Server server = cluster.getServer(serverSelector);
                    connection = server.getAsyncConnection();
                } catch (MongoException e) {
                    exception = e;
                } catch (Throwable t) {
                    exception = new MongoInternalException("Exception getting a connection", t);
                }
                retVal.init(connection, exception);
            }
        });

        return retVal;
    }

    @Override
    public MongoFuture<AsyncServerConnection> getConnection() {
        isTrue("open", !isClosed());

        return getConnection(new PrimaryServerSelector());
    }

    @Override
    public AsyncSession getBoundSession(final ServerSelector serverSelector, final SessionBindingType sessionBindingType) {
        isTrue("open", !isClosed());

        if (sessionBindingType == SessionBindingType.Connection) {
            return new SingleConnectionAsyncSession(serverSelector, this);
        }
        else {
            return new SingleServerAsyncSession(serverSelector, cluster, executor);
        }
    }

    @Override
    public void close() {
        isClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }
}