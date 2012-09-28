package org.agmip.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a simple interface to handle AgMIP bucket entries
 * inside a nested structure. These entries are returned by the AgMIP translators
 * and reflect collections of data (such as weather stations and data, soil
 * information, or field experiment parameters.
 *
 * All methods provided by this class are <code>static</code> 
 * @author Christopher Villalobos
 * @version 0.15
 * @since 0.15
 */

public class MapUtil {
    /**
     * Each <code>BucketEntry</code> corresponds to a section of the AgMIP
     * experiment definition. Currently the core definitions are:
     * <ul>
     * <li>initial_conditions</li>
     * <li>weather</li>
     * <li>soil</li>
     * <li>management</li>
     * <li>observed</li>
     * </ul>
     * 
     * Also, this supports custom per-translator buckets. These will be stored
     * in the database as such and may not be used by other translators. Do not
     * put required information into custom buckets.
     */

    private static final Logger LOG = LoggerFactory.getLogger(MapUtil.class);
    public static class BucketEntry {
        private HashMap<String, String> values = new HashMap();
        private ArrayList<HashMap<String, String>> dataList = new ArrayList();
        private HashMap<String, BucketEntry> subBuckets = new HashMap();

        public BucketEntry(HashMap<String, Object> m) {
            for(Map.Entry<String, Object> entry : m.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if( key.equals("data") || key.equals("soilLayer") || key.equals("dailyWeather") || key.equals("events") || key.equals("timeSeries") ) {
                    this.dataList = (ArrayList<HashMap<String, String>>) value;
                    if(! key.equals("events") ) {
                        this.parseDataList();
                    }
                } else {
                    try {
                        values.put(key, (String) value);
                    } catch (ClassCastException ex) {
                        LOG.error("VALUE INSERTION ERROR ["+key+"]: "+value.toString());
                    }
                }
            }
        }

        public BucketEntry() {}

        /**
         * Returns the non-nested (or top level) data associated with this bucket.
         * 
         * @return the non-nested values associated with this bucket.
         */
        public HashMap<String, String> getValues() {
            return values;
        }

        /**
         * Returns the nested values associated with this bucket.
         * 
         * Each bucket type may have nested values associated with it. For
         * example, soil has soilLayers and weather has dailyWeather.
         * 
         * @return the nested values associated with this bucket.
         */
        public ArrayList<HashMap<String, String>> getDataList() {
            return dataList;
        }

        /**
         * Decompresses data from a data list and populates the local dataList
         * variable.
         * 
         */
        public void parseDataList() {
            ArrayList<HashMap<String, String>> acc = new ArrayList();
            HashMap<String, String> stickyMap = new HashMap();
            for(Map<String, String> sourceMap : dataList) {
                if( acc.size() == 0 ) {
                    for(Map.Entry<String, String> e : sourceMap.entrySet()) {
                        stickyMap.put(e.getKey(), sourceMap.get(e.getKey()));
                    }
                    acc.add(stickyMap);
                } else {
                    HashMap<String, String> mergedMap = new HashMap();
                    for(Map.Entry<String, String> e : stickyMap.entrySet()) {
                        String sourceValue = sourceMap.get(e.getKey());
                        if ( null == sourceValue ) {
                            mergedMap.put(e.getKey(), getValueOr(sourceMap, e.getKey(), e.getValue()));
                        } else if ( sourceValue.equals("") ){ } else {
                            mergedMap.put(e.getKey(), getValueOr(sourceMap, e.getKey(), e.getValue()));
                        }
                    }
                    acc.add(mergedMap);
                }
            }
            this.dataList = acc;
        }
    }

    
    /**
     * 
     * @param m The source map from the translator or database
     * @return an uncompressed version of the map.
     */
    public static HashMap<String, Object> decompressAll(Map<String, Object> m) {
        HashMap<String, Object> all = new HashMap(getGlobalValues(m));
        HashMap<String, String> translate = new HashMap();
        translate.put("initial_conditions", "soilLayer");
        translate.put("soil", "soilLayer");
        translate.put("weather", "dailyWeather");
        translate.put("management", "events");
        translate.put("observed", "timeSeries");

        ArrayList<String> buckets = listBucketNames(m);
        for(String bucket : buckets) {
            BucketEntry b = getBucket(m, bucket);
            HashMap<String, Object> sub = new HashMap<String, Object>(b.getValues());
            String nestedKey = translate.get(bucket);
            if (nestedKey == null) {
                nestedKey = "data";
            }
            sub.put(nestedKey, b.getDataList());
            all.put(bucket, sub);
        }
        return all;
    }


    public static <K,V> V getObjectOr(Map<K,V> m, K key, V orValue ) {
        Optional<V> opt = Optional.fromNullable(m.get(key));
        return opt.or(orValue);
    }

    /**
     * Returns a list of all bucket names in the provided Map.
     */ 
    public static ArrayList<String> listBucketNames(Map<String, Object> m) {
        ArrayList<String> acc = new ArrayList<String>();
        for(Map.Entry<String, Object> entry : m.entrySet()) {
            if( isValidBucket(entry.getValue()) ) {
                acc.add(entry.getKey());
            }
        }
        return acc;
    }

    public static BucketEntry getBucket(Map<String, Object> m, String key) {
        HashMap<String, Object> b = (HashMap<String,Object>) getObjectOr(m, key, new HashMap<String, Object>());
        return new BucketEntry(b);
    }

    public static HashMap<String, String> getGlobalValues(Map<String, Object> m) {
        HashMap<String, String> globals = new HashMap<String, String>();
        for(Map.Entry<String, Object> entry : m.entrySet()) {
            if(entry.getValue() instanceof String) {
                globals.put(entry.getKey(), (String) entry.getValue());
            }
        }
        return globals;
    }

    public static String getValueOr(Map<String, String> m, String key, String orValue) {
        return getObjectOr(m, key, orValue);
    }

    public static HashMap<String, String> flattenGlobals(Map<String, Object> m) {
        HashMap<String, String> globals = new HashMap<String, String>(getGlobalValues(m));
        ArrayList<String> buckets = listBucketNames(m);
        Iterator iter = buckets.iterator();
        
        while(iter.hasNext()) {
            BucketEntry b = getBucket(m, (String) iter.next());
            globals.putAll(b.getValues());
        }

        return globals;
    }

    public static HashMap<String, String> extract(Map<String,Object> m, Set<String> s) {
        HashMap<String, String> extracted = new HashMap<String,String>();
        HashMap<String, String> flatMap = flattenGlobals(m);

        Iterator i = s.iterator();
        while(i.hasNext()) {
            String key = (String) i.next();
            if( flatMap.containsKey(key) ) {
                extracted.put(key, flatMap.get(key));
            }
        }

        return extracted;
    }


    /**
     * Check to see if the passed Object is a valid Bucket
     */
    private static boolean isValidBucket(Object test) {
        if ( test instanceof Map ) {
            return true;
        } else {
            return false;
        }
    }
}
