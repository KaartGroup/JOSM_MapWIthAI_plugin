// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.sources.MapPaintPrefHelper;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDDataUtilsTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences();

    /**
     * This gets data from RapiD. This test may fail if someone adds the data to OSM.
     */
    @Test
    public void testGetData() {
        BBox testBBox = getTestBBox();
        DataSet ds = new DataSet(RapiDDataUtils.getData(testBBox));
        Assert.assertEquals(1, ds.getWays().size());
    }

    @Test
    public void testAddSourceTags() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        DataSet ds = new DataSet(way1.firstNode(), way1.lastNode(), way1);
        String source = "random source";

        Assert.assertNull(way1.get("source"));
        RapiDDataUtils.addSourceTags(ds, "highway", source);
        Assert.assertEquals(source, way1.get("source"));
    }

    public static BBox getTestBBox() {
        BBox testBBox = new BBox();
        testBBox.add(new LatLon(39.076, -108.547));
        testBBox.add(new LatLon(39.078, -108.545));
        return testBBox;
    }

    @Test
    public void testAddPrimitivesToCollection() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        Collection<OsmPrimitive> collection = new TreeSet<>();
        Assert.assertEquals(0, collection.size());
        RapiDDataUtils.addPrimitivesToCollection(collection, Collections.singletonList(way1));
        Assert.assertEquals(3, collection.size());
    }

    @Test
    public void testRemovePrimitivesFromDataSet() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        DataSet ds1 = new DataSet();
        for (Node node : way1.getNodes()) {
            ds1.addPrimitive(node);
        }
        ds1.addPrimitive(way1);

        Assert.assertEquals(3, ds1.allPrimitives().size());
        RapiDDataUtils.removePrimitivesFromDataSet(Collections.singleton(way1));
        Assert.assertEquals(0, ds1.allPrimitives().size());
    }

    @Test
    public void testAddPaintStyle() {
        List<SourceEntry> paintStyles = MapPaintPrefHelper.INSTANCE.get();
        // There are two default paint styles
        Assert.assertEquals(2, paintStyles.size());
        RapiDDataUtils.addRapiDPaintStyles();
        paintStyles = MapPaintPrefHelper.INSTANCE.get();
        Assert.assertEquals(3, paintStyles.size());
        RapiDDataUtils.addRapiDPaintStyles();
        paintStyles = MapPaintPrefHelper.INSTANCE.get();
        Assert.assertEquals(3, paintStyles.size());
        RapiDDataUtils.addRapiDPaintStyles();
    }

    @Test
    public void testRapiDURLPreferences() {
        String fakeUrl = "https://fake.url";
        Assert.assertEquals(RapiDDataUtils.DEFAULT_RAPID_API, RapiDDataUtils.getRapiDURL());
        RapiDDataUtils.setRapiDUrl(fakeUrl);
        Assert.assertEquals(fakeUrl, RapiDDataUtils.getRapiDURL());
        List<String> urls = new ArrayList<>(RapiDDataUtils.getRapiDURLs());
        Assert.assertEquals(2, urls.size());
        RapiDDataUtils.setRapiDUrl(RapiDDataUtils.DEFAULT_RAPID_API);
        Assert.assertEquals(RapiDDataUtils.DEFAULT_RAPID_API, RapiDDataUtils.getRapiDURL());
        RapiDDataUtils.setRapiDUrl(fakeUrl);
        Assert.assertEquals(fakeUrl, RapiDDataUtils.getRapiDURL());
        urls.remove(fakeUrl);
        RapiDDataUtils.setRapiDURLs(urls);
        Assert.assertEquals(RapiDDataUtils.DEFAULT_RAPID_API, RapiDDataUtils.getRapiDURL());
    }

    @Test
    public void testSplitBounds() {
        BBox bbox = new BBox(0, 0, 0.0001, 0.0001);
        List<BBox> bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(1, bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.001, 0.001);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(1, bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.01, 0.01);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(1, bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.1, 0.1);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(4, bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(1, 1);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(144, bboxes.size());
        checkInBBox(bbox, bboxes);
    }

    private void checkInBBox(BBox bbox, Collection<BBox> bboxes) {
        for (BBox tBBox : bboxes) {
            Assert.assertTrue(bbox.bounds(tBBox));
        }
    }
}