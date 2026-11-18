package icdf.sndc.ims.period;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/periods")
public class PeriodController {

    private final PeriodService periodService;

    public PeriodController(PeriodService periodService) {
        this.periodService = periodService;
    }

    /** 所有盤點期別與資產筆數（新到舊）。 */
    @GetMapping
    public List<PeriodDtos.PeriodResponse> list() {
        return periodService.list();
    }
}
