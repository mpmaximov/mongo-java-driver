/**
 * Copyright (c) 2008 - 2012 10gen, Inc. <http://10gen.com>
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
 *
 */

package org.mongodb;

import org.mongodb.rs.ReplicaSet;
import org.mongodb.rs.ReplicaSetNode;
import org.mongodb.rs.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all preference which can be combined with tags
 *
 * @author breinero
 */
public abstract class TaggableReadPreference extends ReadPreference {
    private final static List<MongoDocument> EMPTY = new ArrayList<MongoDocument>();

    TaggableReadPreference() {
        _tags = EMPTY;
    }

    TaggableReadPreference(MongoDocument firstTagSet, MongoDocument... remainingTagSets) {
        if (firstTagSet == null) {
            throw new IllegalArgumentException("Must have at least one tag set");
        }
        _tags = new ArrayList<MongoDocument>();
        _tags.add(firstTagSet);
        Collections.addAll(_tags, remainingTagSets);
    }

    @Override
    public boolean isSlaveOk() {
        return true;
    }

    @Override
    public MongoDocument toMongoDocument() {
        MongoDocument readPrefObject = new MongoDocument("mode", getName());

        if (!_tags.isEmpty())
            readPrefObject.put("tags", _tags);

        return readPrefObject;
    }


    public List<MongoDocument> getTagSets() {
        List<MongoDocument> tags = new ArrayList<MongoDocument>();
        for (MongoDocument tagSet : _tags) {
            tags.add(tagSet);
        }
        return tags;
    }

    @Override
    public String toString() {
        return getName() + printTags();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final TaggableReadPreference that = (TaggableReadPreference) o;

        if (!_tags.equals(that._tags)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = _tags.hashCode();
        result = 31 * result + getName().hashCode();
        return result;
    }

    String printTags() {
        return (_tags.isEmpty() ? "" :  " : " + new MongoDocument("tags", _tags));
    }

    private static List<Tag> getTagListFromMongoDocument(final MongoDocument curTagSet) {
        List<Tag> tagList = new ArrayList<Tag>();
        for (String key : curTagSet.keySet()) {
            tagList.add(new Tag(key, curTagSet.get(key).toString()));
        }
        return tagList;
    }

    final List<MongoDocument> _tags;

    /**
     * Read from secondary
     *
     * @author breinero
     */
    static class SecondaryReadPreference extends TaggableReadPreference {
        SecondaryReadPreference() {
        }

        SecondaryReadPreference(MongoDocument firstTagSet, MongoDocument... remainingTagSets) {
            super(firstTagSet, remainingTagSets);
        }

        @Override
        public String getName() {
            return "secondary";
        }

        @Override
        ReplicaSetNode getNode(ReplicaSet set) {

            if (_tags.isEmpty())
                return set.getASecondary();

            for (MongoDocument curTagSet : _tags) {
                List<Tag> tagList = getTagListFromMongoDocument(curTagSet);
                ReplicaSetNode node = set.getASecondary(tagList);
                if (node != null) {
                    return node;
                }
            }
            return null;
        }

    }

    /**
     * Read from secondary if available, otherwise from primary, irrespective of tags.
     *
     * @author breinero
     */
    static class SecondaryPreferredReadPreference extends SecondaryReadPreference {
        SecondaryPreferredReadPreference() {
        }

        SecondaryPreferredReadPreference(MongoDocument firstTagSet, MongoDocument... remainingTagSets) {
            super(firstTagSet, remainingTagSets);
        }

        @Override
        public String getName() {
            return "secondaryPreferred";
        }

        @Override
        ReplicaSetNode getNode(ReplicaSet set) {
            ReplicaSetNode node = super.getNode(set);
            return (node != null) ? node : set.getMaster();
        }
    }

    /**
     * Read from nearest node respective of tags.
     *
     * @author breinero
     */
    static class NearestReadPreference extends TaggableReadPreference {
        NearestReadPreference() {
        }

        NearestReadPreference(MongoDocument firstTagSet, MongoDocument... remainingTagSets) {
            super(firstTagSet, remainingTagSets);
        }


        @Override
        public String getName() {
            return "nearest";
        }


        @Override
        ReplicaSetNode getNode(ReplicaSet set) {

            if (_tags.isEmpty())
                return set.getAMember();

            for (MongoDocument curTagSet : _tags) {
                List<Tag> tagList = getTagListFromMongoDocument(curTagSet);
                ReplicaSetNode node = set.getAMember(tagList);
                if (node != null) {
                    return node;
                }
            }
            return null;
        }
    }

    /**
     * Read from primary if available, otherwise a secondary.
     *
     * @author breinero
     */
    static class PrimaryPreferredReadPreference extends SecondaryReadPreference {
        PrimaryPreferredReadPreference() {}

        PrimaryPreferredReadPreference(MongoDocument firstTagSet, MongoDocument... remainingTagSets) {
            super(firstTagSet, remainingTagSets);
        }

        @Override
        public String getName() {
            return "primaryPreferred";
        }

        @Override
        ReplicaSetNode getNode(ReplicaSet set) {
            ReplicaSetNode node = set.getMaster();
            return (node != null) ? node : super.getNode(set);
        }
    }
}
