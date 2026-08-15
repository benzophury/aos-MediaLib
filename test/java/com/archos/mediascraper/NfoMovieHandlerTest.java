// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import com.archos.mediascraper.saxhandler.NfoMovieHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NfoMovieHandlerTest {

    @Test
    public void readsTmdbUniqueId() throws Exception {
        MovieTags tags = parse("<movie><title>Film</title>"
                + "<uniqueid type=\"tmdb\" default=\"true\">550</uniqueid></movie>");

        assertEquals(550, tags.getOnlineId());
    }

    @Test
    public void tmdbUniqueIdWinsOverLegacyTmdbId() throws Exception {
        MovieTags tags = parse("<movie><title>Film</title>"
                + "<tmdbid>999</tmdbid><uniqueid type=\"tmdb\">550</uniqueid></movie>");

        assertEquals(550, tags.getOnlineId());
    }

    @Test
    public void imdbUniqueIdWinsOverLegacyIdRegardlessOfOrder() throws Exception {
        // movie <id> carries the imdb id in the legacy format
        MovieTags tags = parse("<movie><title>Film</title>"
                + "<uniqueid type=\"imdb\">tt0137523</uniqueid><id>tt0000001</id></movie>");

        assertEquals("tt0137523", tags.getImdbId());
    }

    @Test
    public void parsesStashNfoFormat() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n"
                + "<movie>\n"
                + "    <title>She'll do anything to help her boyfriend from his boss</title>\n"
                + "    <originaltitle>She'll do anything to help her boyfriend from his boss</originaltitle>\n"
                + "    <studio>Big Cock Bully</studio>\n"
                + "    <year>2026</year>\n"
                + "    <premiered>2026-08-13</premiered>\n"
                + "    <releasedate>2026-08-13</releasedate>\n"
                + "    <runtime>33</runtime>\n"
                + "    <duration>2003</duration>\n"
                + "    <actor>\n"
                + "        <name>Lawson Jones</name>\n"
                + "        <role>Actor</role>\n"
                + "        <type>Actor</type>\n"
                + "    </actor>\n"
                + "    <actor>\n"
                + "        <name>Selina Imai</name>\n"
                + "        <role>Actor</role>\n"
                + "        <type>Actor</type>\n"
                + "    </actor>\n"
                + "    <uniqueid type=\"stashdb\" default=\"true\">019ffba9-44c1-70d1-af11-8e742cf2031d</uniqueid>\n"
                + "</movie>";

        MovieTags tags = parse(xml);

        assertEquals("She'll do anything to help her boyfriend from his boss", tags.getTitle());
        assertEquals("019ffba9-44c1-70d1-af11-8e742cf2031d", tags.getImdbId());
        assertEquals(2026, tags.getYear());
        assertEquals("2026-08-13", tags.getReleaseDate());
        assertEquals("Big Cock Bully", tags.getDirectorsFormatted());
        assertEquals("Big Cock Bully", tags.getStudiosFormatted());
        assertEquals(33, tags.getRuntime(java.util.concurrent.TimeUnit.MINUTES));
        assertEquals("Lawson Jones, Selina Imai", tags.getActorsFormatted());
    }

    @Test
    public void parsesTextDatesCorrectly() throws Exception {
        String xml = "<movie>\n"
                + "    <title>Post-flight Tribbulance</title>\n"
                + "    <studio>AdultTime.com</studio>\n"
                + "    <premiered>Nov 14, 2025</premiered>\n"
                + "    <actor>\n"
                + "        <name>Scarlett Alexis</name>\n"
                + "    </actor>\n"
                + "</movie>";

        MovieTags tags = parse(xml);
        assertEquals("Post-flight Tribbulance", tags.getTitle());
        assertEquals("AdultTime.com", tags.getDirectorsFormatted());
        assertEquals("AdultTime.com", tags.getStudiosFormatted());
        assertEquals(2025, tags.getYear());
        assertEquals("Nov 14, 2025", tags.getReleaseDate());
        assertEquals("Scarlett Alexis", tags.getActorsFormatted());
    }

    private static MovieTags parse(String xml) throws Exception {
        NfoMovieHandler handler = new NfoMovieHandler();
        NfoParser.getNewParser().parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)), handler);
        return handler.getResult(null, Uri.parse("file:///film.mkv"));
    }
}
