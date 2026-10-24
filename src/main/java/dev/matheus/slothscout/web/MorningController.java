package dev.matheus.slothscout.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import dev.matheus.slothscout.digest.Digest;
import dev.matheus.slothscout.digest.DigestRepository;
import dev.matheus.slothscout.job.JobPosting;
import dev.matheus.slothscout.job.JobRepository;
import dev.matheus.slothscout.job.JobStatus;
import dev.matheus.slothscout.pipeline.ScoutPipeline;

/** A página da manhã: as duas linhas, a lista curta e o resto recolhido. */
@Controller
public class MorningController {

	private final DigestRepository digests;

	private final JobRepository jobs;

	private final ScoutPipeline pipeline;

	public MorningController(DigestRepository digests, JobRepository jobs, ScoutPipeline pipeline) {
		this.digests = digests;
		this.jobs = jobs;
		this.pipeline = pipeline;
	}

	@GetMapping("/")
	public String latest(Model model) {
		return render(digests.findFirstByOrderByDayDesc(), model);
	}

	@GetMapping("/d/{day}")
	public String day(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day, Model model) {
		return render(digests.findById(day), model);
	}

	@PostMapping("/run")
	public String run() {
		if (!pipeline.isRunning()) {
			Thread.ofVirtual().name("scout-manual").start(pipeline::run);
		}
		return "redirect:/";
	}

	private String render(Optional<Digest> digest, Model model) {
		model.addAttribute("running", pipeline.isRunning());
		model.addAttribute("lastMessage", pipeline.lastMessage());
		model.addAttribute("pending", jobs.countByStatus(JobStatus.NEW));
		model.addAttribute("days", digests.findAll().stream().map(Digest::getDay).sorted().toList().reversed());
		if (digest.isEmpty()) {
			model.addAttribute("digest", null);
			return "morning";
		}
		Digest d = digest.get();
		List<JobPosting> all = jobs.findByDigestDateOrderByScoreDesc(d.getDay());
		model.addAttribute("digest", d);
		model.addAttribute("headline", d.getHeadline().lines().toList());
		model.addAttribute("shortlist", all.stream().filter(j -> j.getScore() >= d.getCutoff()).toList());
		model.addAttribute("ignored", all.stream().filter(j -> j.getScore() < d.getCutoff()).toList());
		return "morning";
	}
}
