// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class DownloadMapWithAITaskTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rule = new MapWithAITestRules().sources().wiremock().preferences().fakeAPI().projection()
            .territories();

    @Test
    void testDownloadOsmServerReaderDownloadParamsBoundsProgressMonitor()
            throws InterruptedException, ExecutionException {
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        Future<?> future = task.download(
                new BoundingBoxMapWithAIDownloader(MapWithAIDataUtilsTest.getTestBounds(),
                        MapWithAILayerInfo.getInstance().getLayers().get(0), false),
                new DownloadParams(), MapWithAIDataUtilsTest.getTestBounds(), NullProgressMonitor.INSTANCE);
        future.get();
        assertNotNull(task.getDownloadedData(), "Data should be downloaded");
    }

    @Test
    void testGetConfirmationMessage() throws MalformedURLException {
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        assertAll(
                () -> assertTrue(task.getConfirmationMessage(new URL("https://fake.api")).contains("fake.api"),
                        "We should get a confirmation message"),
                () -> assertNull(task.getConfirmationMessage(null), "The message should be null if the URL is null"));
    }

}
