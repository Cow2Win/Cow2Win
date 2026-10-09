package org.c2w.datatool.data;

import java.util.List;

/**
 * Outcome of {@link DataSet#save}.
 *
 * @param written files written, relative to the resources folder (with {@code /})
 * @param errors  check errors that prevented saving - then nothing was written
 */
public record SaveResult(List<String> written, List<Problem> errors) {

    public boolean saved() {
        return errors.isEmpty();
    }
}
