/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.AirReceiver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.*;
import java.util.Date;
import java.util.logging.*;

/**
 * Java.util.logging single-line log formatter
 */
public class LogFormatter extends Formatter {
	static final DateFormat DateFormater = new SimpleDateFormat("yyyy.MM.dd HH:mm.ss.SSS");

	@Override
	public String format(final LogRecord record) {
		final StringBuilder s = new StringBuilder();

		s.append(DateFormater.format(new Date(record.getMillis())));
		s.append(" ");
		s.append(record.getLevel() != null ? record.getLevel().getName() : "?");
		s.append(" ");
		s.append(record.getLoggerName() != null ? record.getLoggerName() : "");
		s.append(" ");
		s.append(record.getMessage() != null ? record.getMessage() : "");

		if (record.getThrown() != null) {
			String stackTrace;
			{
				final Throwable throwable = record.getThrown();
				final StringWriter stringWriter = new StringWriter();
				final PrintWriter stringPrintWriter = new PrintWriter(stringWriter);
				throwable.printStackTrace(stringPrintWriter);
				stringPrintWriter.flush();
				stackTrace = stringWriter.toString();
			}

			s.append(String.format("%n"));
			s.append(stackTrace);
		}

		s.append(String.format("%n"));

		return s.toString();
	}

}
