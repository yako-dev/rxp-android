package com.realexpayments.remote;

import androidx.test.filters.SmallTest;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class ValidateExpiryDateNotInPastTest{

    @SmallTest
    public void testDateInPast() {
        assertFalse(RealexRemote.validateExpiryDateNotInPast("0615"));
    }

    @SmallTest
    public void testCurrentMonth() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("MMyy");
        assertTrue(RealexRemote.validateExpiryDateNotInPast(format.format(calendar.getTime())));
    }

}