package org.c2w.eval;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;

public interface LineupAlgorithm {

    String displayName();

    Lineup run(Lineup lineup, Guild guild);
}
