version 1.0

import "Structs.wdl"
import "CollectCoverage.wdl" as cov
import "CramToBam.wdl" as ctb
import "GermlineCNVCohort.wdl" as gcnv_cohort

# Trains gCNV model on a cohort with counts already collected
workflow TrainGCNV {
  input {
    Array[String] samples
    Array[File] count_files

    # Common parameters
    String cohort
    File reference_fasta
    File reference_index    # Index (.fai), must be in same dir as fasta
    File reference_dict     # Dictionary (.dict), must be in same dir as fasta

    # Condense read counts
    Int? condense_num_bins
    Int? condense_bin_size

    # gCNV common inputs
    Int? gcnv_preemptible_attempts
    Int ref_copy_number_autosomal_contigs
    Array[String]? allosomal_contigs

    # Interval filtering inputs
    File? blacklist_intervals_for_filter_intervals_ploidy
    File? blacklist_intervals_for_filter_intervals_cnv
    Int? disk_space_gb_for_filter_intervals
    Int? mem_gb_for_filter_intervals

    # gCNV cohort mode inputs
    Boolean? filter_intervals
    File contig_ploidy_priors
    Int num_intervals_per_scatter
    Boolean? do_explicit_gc_correction
    Boolean? gcnv_enable_bias_factors
    Int? mem_gb_for_determine_germline_contig_ploidy
    Int? cpu_for_determine_germline_contig_ploidy
    Int? mem_gb_for_gcnv_cohort_caller
    Int? cpu_for_gcnv_cohort_caller

    # gCNV additional arguments
    Float? gcnv_learning_rate
    Int? gcnv_num_thermal_advi_iters
    Int? gcnv_max_advi_iter_first_epoch
    Int? gcnv_max_advi_iter_subsequent_epochs
    Int? gcnv_max_training_epochs
    Int? gcnv_min_training_epochs
    Int? gcnv_convergence_snr_averaging_window
    Int? gcnv_convergence_snr_countdown_window
    Int? gcnv_cnv_coherence_length
    String? gcnv_copy_number_posterior_expectation_mode
    Int? gcnv_log_emission_sampling_rounds
    Float? gcnv_p_alt
    Float? gcnv_sample_psi_scale
    Float? ploidy_sample_psi_scale

    Float? gcnv_caller_update_convergence_threshold
    Float? gcnv_class_coherence_length
    Float? gcnv_convergence_snr_trigger_threshold
    Float? gcnv_interval_psi_scale
    Float? gcnv_log_emission_sampling_median_rel_error
    Float? gcnv_log_mean_bias_standard_deviation
    Int? gcnv_max_bias_factors
    Int? gcnv_max_calling_iters
    Float? ploidy_global_psi_scale
    Float? ploidy_mean_bias_standard_deviation
    Float? gcnv_depth_correction_tau
    Int? postprocessing_mem_gb

    # gCNV model building arguments
    Float? gcnv_model_learning_rate
    Int? gcnv_model_num_thermal_advi_iters
    Int? gcnv_model_max_advi_iter_first_epoch
    Int? gcnv_model_max_advi_iter_subsequent_epochs
    Int? gcnv_model_max_training_epochs
    Int? gcnv_model_min_training_epochs
    Int? gcnv_model_convergence_snr_averaging_window
    Int? gcnv_model_convergence_snr_countdown_window
    Int? gcnv_model_cnv_coherence_length
    Int? gcnv_model_log_emission_sampling_rounds

    # Docker
    String sv_mini_docker
    String linux_docker
    String gatk_docker
    String condense_counts_docker

    # Runtime configuration overrides
    RuntimeAttr? condense_counts_runtime_attr
    RuntimeAttr? counts_to_intervals_runtime_attr
  }

  scatter (i in range(length(samples))) {
    call cov.CondenseReadCounts as CondenseReadCounts {
      input:
        counts = count_files[i],
        sample = samples[i],
        num_bins = condense_num_bins,
        expected_bin_size = condense_bin_size,
        condense_counts_docker = condense_counts_docker,
        runtime_attr_override=condense_counts_runtime_attr
    }
  }

  call cov.CountsToIntervals {
    input:
      counts = CondenseReadCounts.out[0],
      output_name = "condensed_intervals",
      linux_docker = linux_docker,
      runtime_attr_override = counts_to_intervals_runtime_attr
  }

  call gcnv_cohort.CNVGermlineCohortWorkflow {
    input:
      preprocessed_intervals = CountsToIntervals.out,
      filter_intervals = filter_intervals,
      counts = CondenseReadCounts.out,
      count_entity_ids = samples,
      cohort_entity_id = cohort,
      contig_ploidy_priors = contig_ploidy_priors,
      num_intervals_per_scatter = num_intervals_per_scatter,
      ref_fasta_dict = reference_dict,
      ref_fasta_fai = reference_index,
      ref_fasta = reference_fasta,
      blacklist_intervals_for_filter_intervals_ploidy=blacklist_intervals_for_filter_intervals_ploidy,
      blacklist_intervals_for_filter_intervals_cnv=blacklist_intervals_for_filter_intervals_cnv,
      disk_space_gb_for_filter_intervals = disk_space_gb_for_filter_intervals,
      mem_gb_for_filter_intervals = mem_gb_for_filter_intervals,
      do_explicit_gc_correction = do_explicit_gc_correction,
      gcnv_enable_bias_factors = gcnv_enable_bias_factors,
      ref_copy_number_autosomal_contigs = ref_copy_number_autosomal_contigs,
      allosomal_contigs = allosomal_contigs,
      mem_gb_for_determine_germline_contig_ploidy = mem_gb_for_determine_germline_contig_ploidy,
      mem_gb_for_germline_cnv_caller = mem_gb_for_gcnv_cohort_caller,
      cpu_for_germline_cnv_caller = cpu_for_gcnv_cohort_caller,
      gatk_docker = gatk_docker,
      linux_docker = linux_docker,
      preemptible_attempts = gcnv_preemptible_attempts,
      gcnv_learning_rate = gcnv_learning_rate,
      gcnv_max_advi_iter_first_epoch = gcnv_max_advi_iter_first_epoch,
      gcnv_num_thermal_advi_iters = gcnv_model_num_thermal_advi_iters,
      gcnv_max_advi_iter_subsequent_epochs = gcnv_model_max_advi_iter_subsequent_epochs,
      gcnv_max_training_epochs = gcnv_model_max_training_epochs,
      gcnv_min_training_epochs = gcnv_model_min_training_epochs,
      gcnv_convergence_snr_averaging_window = gcnv_model_convergence_snr_averaging_window,
      gcnv_convergence_snr_countdown_window = gcnv_model_convergence_snr_countdown_window,
      gcnv_cnv_coherence_length = gcnv_model_cnv_coherence_length,
      gcnv_class_coherence_length = gcnv_class_coherence_length,
      gcnv_copy_number_posterior_expectation_mode = gcnv_copy_number_posterior_expectation_mode,
      gcnv_log_emission_sampling_rounds = gcnv_model_log_emission_sampling_rounds,
      gcnv_p_alt = gcnv_p_alt,
      gcnv_sample_psi_scale = gcnv_sample_psi_scale,
      ploidy_sample_psi_scale = ploidy_sample_psi_scale,
      gcnv_caller_update_convergence_threshold = gcnv_caller_update_convergence_threshold,
      gcnv_convergence_snr_trigger_threshold = gcnv_convergence_snr_trigger_threshold,
      gcnv_interval_psi_scale = gcnv_interval_psi_scale,
      gcnv_log_emission_sampling_median_rel_error = gcnv_log_emission_sampling_median_rel_error,
      gcnv_log_mean_bias_standard_deviation = gcnv_log_mean_bias_standard_deviation,
      gcnv_max_bias_factors = gcnv_max_bias_factors,
      gcnv_max_calling_iters = gcnv_max_calling_iters,
      ploidy_global_psi_scale = ploidy_global_psi_scale,
      ploidy_mean_bias_standard_deviation = ploidy_mean_bias_standard_deviation,
      gcnv_depth_correction_tau = gcnv_depth_correction_tau,
      postprocessing_mem_gb = postprocessing_mem_gb
  }

  output {
    File? annotated_intervals = CNVGermlineCohortWorkflow.annotated_intervals
    File? filtered_intervals_cnv = CNVGermlineCohortWorkflow.filtered_intervals_cnv
    File? filtered_intervals_ploidy = CNVGermlineCohortWorkflow.filtered_intervals_ploidy
    File? cohort_contig_ploidy_model_tar = CNVGermlineCohortWorkflow.contig_ploidy_model_tar
    File? cohort_contig_ploidy_calls_tar = CNVGermlineCohortWorkflow.contig_ploidy_calls_tar
    Array[File]? cohort_gcnv_model_tars = CNVGermlineCohortWorkflow.gcnv_model_tars
    Array[File]? cohort_gcnv_calls_tars = CNVGermlineCohortWorkflow.gcnv_calls_tars
    Array[File]? cohort_gcnv_tracking_tars = CNVGermlineCohortWorkflow.gcnv_tracking_tars
    Array[File]? cohort_genotyped_intervals_vcfs = CNVGermlineCohortWorkflow.genotyped_intervals_vcfs
    Array[File]? cohort_genotyped_segments_vcfs = CNVGermlineCohortWorkflow.genotyped_segments_vcfs
  }
}
