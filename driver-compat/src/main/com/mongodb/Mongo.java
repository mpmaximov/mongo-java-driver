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

package com.mongodb;

import org.mongodb.Document;
import org.mongodb.MongoConnection;
import org.mongodb.annotations.ThreadSafe;
import org.mongodb.command.ListDatabases;
import org.mongodb.impl.MongoConnectionsImpl;
import org.mongodb.serialization.PrimitiveSerializers;
import org.mongodb.serialization.Serializer;
import org.mongodb.serialization.serializers.DocumentSerializer;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class Mongo {
    private static final String ADMIN_DATABASE_NAME = "admin";
    private static final String VERSION = "3.0.0-SNAPSHOT";

    private final ConcurrentMap<String, DB> dbCache = new ConcurrentHashMap<String, DB>();

    private volatile WriteConcern writeConcern;
    private volatile ReadPreference readPreference;

    private final Serializer<Document> documentSerializer;
    private final MongoConnection connection;

    Mongo(final List<ServerAddress> seedList, final MongoClientOptions mongoOptions) {
        this(MongoConnectionsImpl.create(createNewSeedList(seedList), mongoOptions.toNew()), mongoOptions);
    }

    Mongo(final MongoClientURI mongoURI) throws UnknownHostException {
        this(createConnection(mongoURI.toNew()), mongoURI.getOptions());
    }

    Mongo(final ServerAddress serverAddress, final MongoClientOptions mongoOptions) {
        this(MongoConnectionsImpl.create(serverAddress.toNew(), mongoOptions.toNew()), mongoOptions);
    }

    Mongo(final MongoConnection connection, final MongoClientOptions options) {
        this.connection = connection;
        this.documentSerializer = new DocumentSerializer(PrimitiveSerializers.createDefault());
        this.readPreference = options.getReadPreference() != null ?
                options.getReadPreference() : ReadPreference.primary();
        this.writeConcern = options.getWriteConcern() != null ?
                options.getWriteConcern() : WriteConcern.UNACKNOWLEDGED;
    }

    /**
     * Sets the write concern for this database. Will be used as default for writes to any collection in any database.
     * See the documentation for {@link WriteConcern} for more information.
     *
     * @param writeConcern write concern to use
     */
    public void setWriteConcern(final WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }


    /**
     * Gets the default write concern
     *
     * @return the default write concern
     */
    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    /**
     * Sets the read preference for this database. Will be used as default for reads from any collection in any
     * database. See the documentation for {@link ReadPreference} for more information.
     *
     * @param readPreference Read Preference to use
     */
    public void setReadPreference(final ReadPreference readPreference) {
        this.readPreference = readPreference;
    }

    /**
     * Gets the default read preference
     *
     * @return the default read preference
     */
    public ReadPreference getReadPreference() {
        return readPreference;
    }

    /**
     * Gets the current driver version.
     *
     * @return the full version string, e.g. "3.0.0"
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Gets the list of server addresses currently seen by the connector. This includes addresses auto-discovered from a
     * replica set.
     *
     * @return list of server addresses
     * @throws MongoException
     */
    public List<ServerAddress> getServerAddressList() {
        List<ServerAddress> retVal = new ArrayList<ServerAddress>();
        for (org.mongodb.ServerAddress serverAddress : getConnection().getServerAddressList()) {
            retVal.add(new ServerAddress(serverAddress));
        }
        return retVal;
    }


    /**
     * Gets a list of the names of all databases on the connected server.
     *
     * @return list of database names
     * @throws MongoException
     */
    public List<String> getDatabaseNames() {
        final org.mongodb.result.CommandResult listDatabasesResult;
        try {
            listDatabasesResult = getConnection().command(ADMIN_DATABASE_NAME, new ListDatabases(), documentSerializer);
        } catch (org.mongodb.MongoException e) {
            throw new MongoException(e);
        }

        @SuppressWarnings("unchecked")
        final List<Document> databases = (List<Document>) listDatabasesResult.getResponse().get("databases");

        final List<String> databaseNames = new ArrayList<String>();
        for (final Document d : databases) {
            databaseNames.add(d.get("name", String.class));
        }
        return Collections.unmodifiableList(databaseNames);
    }

    /**
     * Gets a database object
     *
     * @param dbName the name of the database to retrieve
     * @return a DB representing the specified database
     */
    public DB getDB(final String dbName) {
        DB db = dbCache.get(dbName);
        if (db != null) {
            return db;
        }

        db = new DB(this, dbName, documentSerializer);
        final DB temp = dbCache.putIfAbsent(dbName, db);
        if (temp != null) {
            return temp;
        }
        return db;
    }

    /**
     * Returns the list of databases used by the driver since this Mongo instance was created.
     * This may include DBs that exist in the client but not yet on the server.
     *
     * @return a collection of database objects
     */
    public Collection<DB> getUsedDatabases() {
        return dbCache.values();
    }


    /**
     * Drops the database if it exists.
     *
     * @param dbName name of database to drop
     * @throws MongoException
     */
    public void dropDatabase(final String dbName) {
        getDB(dbName).dropDatabase();
    }

    /**
     * Closes all resources associated with this instance, in particular any open network connections. Once called, this
     * instance and any databases obtained from it can no longer be used.
     */
    public void close() {
        getConnection().close();
    }

    //******* Missing functionality from the old driver *******//

    void requestStart() {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    void requestDone() {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    public void addOption(final int option) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    public int getOptions() {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    private static List<org.mongodb.ServerAddress> createNewSeedList(final List<ServerAddress> seedList) {
        List<org.mongodb.ServerAddress> retVal = new ArrayList<org.mongodb.ServerAddress>(seedList.size());
        for (ServerAddress cur : seedList) {
            retVal.add(cur.toNew());
        }
        return retVal;
    }

    private static MongoConnection createConnection(final org.mongodb.MongoClientURI mongoURI) throws UnknownHostException {
        if (mongoURI.getHosts().size() == 1) {
            return MongoConnectionsImpl.create(new org.mongodb.ServerAddress(mongoURI.getHosts().get(0)), mongoURI.getOptions());
        } else {
            List<org.mongodb.ServerAddress> seedList = new ArrayList<org.mongodb.ServerAddress>();
            for (String cur : mongoURI.getHosts()) {
                seedList.add(new org.mongodb.ServerAddress(cur));
            }
            return MongoConnectionsImpl.create(seedList, mongoURI.getOptions());
        }
    }

    protected MongoConnection getConnection() {
        return connection;
    }

}