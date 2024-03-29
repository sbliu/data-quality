// ============================================================================
//
// Copyright (C) 2006-2021 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.dataquality.converters;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DecimalStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this class is used for Converting a date from one Chronology to another with the format pattern.<br/>
 * created by msjian on 2017.1.20 <br/>
 * <br/>
 * <p>
 * for example: the date Chronology type and date string as follows:<br/>
 * HijrahChronology 1432-09-19<br/>
 * IsoChronology 2011/08/19<br/>
 * JapaneseChronology 0023-08-19<br/>
 * MinguoChronology 0100 08 19<br/>
 * ThaiBuddhistChronology 2554-08-19<br/>
 */
public class DateCalendarConverter {

    private static final Logger LOG = LoggerFactory.getLogger(DateCalendarConverter.class);

    private static final String PATTERN_SUFFIX_ERA = "G"; //$NON-NLS-1$

    public static final String DEFAULT_PATTERN = "yyyy-MM-dd";//$NON-NLS-1$

    public static final String DEFAULT_PATTERN_WITH_ERA = DEFAULT_PATTERN + ' ' + PATTERN_SUFFIX_ERA;//$NON-NLS-1$

    public static final Locale DEFAULT_OUTPUT_LOCALE = Locale.getDefault();

    public static final Locale DEFAULT_INPUT_LOCALE = Locale.getDefault();

    /**
     * the input date text format pattern, default is "yyyy-MM-dd".
     */
    protected String inputFormatPattern;

    /**
     * the output date text format pattern, default is "yyyy-MM-dd".
     */
    protected String outputFormatPattern;

    /**
     * an optional input Chronology. default is IsoChronology
     */
    protected Chronology inputChronologyType;

    /**
     * an optional output Chronology. default is IsoChronology
     */
    protected Chronology outputChronologyType;

    /**
     * the input DateTimeFormatter(which will be created with inputFormatPattern and inputChronologyType).
     */
    protected DateTimeFormatter inputDateTimeFormatter;

    /**
     * the output DateTimeFormatter(which will be created with outputFormatPattern and outputChronologyType).
     */
    protected DateTimeFormatter outputDateTimeFormatter;

    public DateCalendarConverter() {
        this(DEFAULT_PATTERN, DEFAULT_PATTERN, IsoChronology.INSTANCE, IsoChronology.INSTANCE, DEFAULT_INPUT_LOCALE,
                DEFAULT_OUTPUT_LOCALE);
    }

    /**
     * DateCalendarConverter constructor.
     *
     * @param inputChronologyType
     * @param outputChronologyType
     */
    public DateCalendarConverter(Chronology inputChronologyType, Chronology outputChronologyType) {
        this(DEFAULT_PATTERN, DEFAULT_PATTERN, inputChronologyType, outputChronologyType, DEFAULT_INPUT_LOCALE,
                DEFAULT_OUTPUT_LOCALE);
    }

    /**
     * DateCalendarConverter constructor.
     *
     * @param inputFormatPattern
     * @param outputFormatPattern
     */
    public DateCalendarConverter(String inputFormatPattern, String outputFormatPattern) {
        this(inputFormatPattern, outputFormatPattern, null, null, DEFAULT_INPUT_LOCALE, DEFAULT_OUTPUT_LOCALE);
    }

    /**
     * Convert a date by change the chronology, and use specific locale to manage month as literal.
     *
     * @param inputFormatPattern Pattern of the input date to convert.
     * @param outputFormatPattern Pattern of the conversion result.
     * @param inputChronologyType Chronology of the input date.
     * @param outputChronologyType Chronology we want to use to convert the date.
     * @param inputLocale Locale of the input date, when the date contains month in literal.
     * @param outputLocale Locale of the converted date when the date contains month in literal.
     */
    public DateCalendarConverter(String inputFormatPattern, String outputFormatPattern, Chronology inputChronologyType,
            Chronology outputChronologyType, Locale inputLocale, Locale outputLocale) {
        this(inputFormatPattern, outputFormatPattern, inputChronologyType, outputChronologyType);
        this.inputDateTimeFormatter = this.inputDateTimeFormatter.withLocale(inputLocale);
        this.outputDateTimeFormatter = this.outputDateTimeFormatter.withLocale(outputLocale);
    }

    /**
     * DateCalendarConverter constructor.
     *
     * @param inputFormatPattern
     * @param outputFormatPattern
     * @param inputChronologyType
     * @param outputChronologyType
     */
    public DateCalendarConverter(String inputFormatPattern, String outputFormatPattern, Chronology inputChronologyType,
            Chronology outputChronologyType) {
        this.inputChronologyType = inputChronologyType == null ? IsoChronology.INSTANCE : inputChronologyType;
        this.outputChronologyType = outputChronologyType == null ? IsoChronology.INSTANCE : outputChronologyType;
        this.inputFormatPattern = inputFormatPattern == null ? DEFAULT_PATTERN : inputFormatPattern;
        if (outputFormatPattern == null) {
            this.outputFormatPattern = JapaneseChronology.INSTANCE.equals(this.outputChronologyType)
                    ? DEFAULT_PATTERN_WITH_ERA : DEFAULT_PATTERN;
        } else {
            this.outputFormatPattern = outputFormatPattern;
        }

        // TDQ-14421 use ResolverStyle.STRICT to validate a date. such as "2017-02-29" should be
        // invalid.STRICT model for pattern without G,should replace 'y' with 'u'.see Java DOC.
        if (!this.inputFormatPattern.contains(PATTERN_SUFFIX_ERA)) {
            this.inputFormatPattern = this.inputFormatPattern.replace('y', 'u');
        }
        this.inputDateTimeFormatter = new DateTimeFormatterBuilder()
                .parseLenient()
                .appendPattern(this.inputFormatPattern)
                .toFormatter()
                .withChronology(this.inputChronologyType)
                .withResolverStyle(ResolverStyle.STRICT)
                .withDecimalStyle(DecimalStyle.of(Locale.getDefault(Locale.Category.FORMAT)));

        this.outputDateTimeFormatter = new DateTimeFormatterBuilder()
                .parseLenient()
                .appendPattern(this.outputFormatPattern)
                .toFormatter()
                .withChronology(this.outputChronologyType)
                .withDecimalStyle(DecimalStyle.of(Locale.getDefault(Locale.Category.FORMAT)));
    }

    /**
     * Convert an inputFormatPattern date text from inputChronologyType to outputChronologyType with outputFormatPattern.
     *
     * @param inputDateStr - the date text need to convert.
     * @return a outputChronologyType text with the outputFormatPattern. note: if can not parse the dateStr with the
     * inputFormatPattern, will return "".
     */
    public String convert(String inputDateStr) {
        if (inputDateStr == null || "".equals(inputDateStr.trim())) { //$NON-NLS-1$
            return inputDateStr;
        }

        if (inputChronologyType.equals(outputChronologyType) && inputFormatPattern.equals(outputFormatPattern)) {
            return inputDateStr;
        }
        LocalDate inputLocalDate = parseStringToDate(inputDateStr);
        return formatDateToString(inputLocalDate);
    }

    /**
     * Converts a LocalDate (ISO) value to a ChronoLocalDate date
     * using the outputChronologyType, and then formats the
     * ChronoLocalDate to a String using outputDateTimeFormatter.
     *
     * @param inputLocalDate - the ISO date to convert and format.
     * @return String
     */
    protected String formatDateToString(LocalDate inputLocalDate) {
        if (inputLocalDate != null) {
            ChronoLocalDate cDate;
            try {
                cDate = outputChronologyType.date(inputLocalDate);
            } catch (DateTimeException ex) {
                LOG.error(ex.getMessage(), ex);
                cDate = inputLocalDate;
            }
            try {
                return outputDateTimeFormatter.format(cDate);
            } catch (DateTimeException ex) {
                LOG.error(ex.getMessage(), ex);
                return ""; //$NON-NLS-1$
            }
        } else {
            return ""; //$NON-NLS-1$
        }
    }

    /**
     * Parses a String to a ChronoLocalDate using inputDateTimeFormatter
     * with inputFormatPattern based on the current Locale and the
     * provided inputChronologyType, then converts this to a LocalDate (ISO)
     * value.
     *
     * @param inputDateStr - the input date text
     * @return LocalDate
     */
    protected LocalDate parseStringToDate(String inputDateStr) {
        try {
            TemporalAccessor temporal = inputDateTimeFormatter.parse(inputDateStr);
            ChronoLocalDate cDate = inputChronologyType.date(temporal);
            return LocalDate.from(cDate);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

}
