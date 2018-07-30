package io.dockstore.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

import io.dockstore.provision.ProvisionInterface;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class FileProvisionTest {

    @Test
    public void testFindSupportedTargetPath() {
        ProvisionInterface s3Mock = Mockito.mock(ProvisionInterface.class);
        when(s3Mock.schemesHandled()).thenReturn(new HashSet<>(Arrays.asList("s3")));
        ProvisionInterface httpMock = Mockito.mock(ProvisionInterface.class);
        when(httpMock.schemesHandled()).thenReturn(new HashSet<>(Arrays.asList("http")));

        assertEquals(FileProvisioning.findSupportedTargetPath(Arrays.asList(s3Mock, httpMock),
                Arrays.asList("s3://something", "http://something")),
                Optional.of(new ImmutablePair<>("s3://something", "s3")));

        assertEquals(FileProvisioning.findSupportedTargetPath(Arrays.asList(s3Mock, httpMock),
                Arrays.asList("http://something", "s3://something")),
                Optional.of(new ImmutablePair<>("http://something", "http")));

        assertEquals(FileProvisioning.findSupportedTargetPath(Arrays.asList(s3Mock, httpMock),
                Arrays.asList("gcs://something")), Optional.empty());
    }
}
