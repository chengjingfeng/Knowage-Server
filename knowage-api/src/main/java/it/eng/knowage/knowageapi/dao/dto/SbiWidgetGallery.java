package it.eng.knowage.knowageapi.dao.dto;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the sbi_widget_gallery database table.
 *
 */
@Entity
@Table(name = "sbi_widget_gallery")
@NamedQuery(name = "SbiWidgetGallery.findAll", query = "SELECT s FROM SbiWidgetGallery s")
public class SbiWidgetGallery implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID uuid;

	private String author;

	private String description;

	@Column(name = "LICENSE_NAME")
	private String licenseName;

	@Lob
	@Column(name = "LICENSE_TEXT")
	private String licenseText;

	@Column(name = "META_VERSION")
	private String metaVersion;

	private String name;

	private String organization;

	@Lob
	@Column(name = "PREVIEW_IMAGE")
	private byte[] previewImage;

	@Column(name = "SBI_VERSION_DE")
	private String sbiVersionDe;

	@Column(name = "SBI_VERSION_IN")
	private String sbiVersionIn;

	@Column(name = "SBI_VERSION_UP")
	private String sbiVersionUp;

	@Lob
	private String template;

	@Column(name = "TIME_DE")
	private Timestamp timeDe;

	@Column(name = "TIME_IN")
	private Timestamp timeIn;

	@Column(name = "TIME_UP")
	private Timestamp timeUp;

	private String type;

	@Column(name = "USAGE_COUNTER")
	private int usageCounter;

	@Column(name = "USER_DE")
	private String userDe;

	@Column(name = "USER_IN")
	private String userIn;

	@Column(name = "USER_UP")
	private String userUp;

	// bi-directional many-to-one association to SbiWidgetGalleryTag
	@OneToMany(mappedBy = "sbiWidgetGallery", fetch = FetchType.EAGER)
	private List<SbiWidgetGalleryTag> sbiWidgetGalleryTags;

	public SbiWidgetGallery() {
	}

	public UUID getUuid() {
		return this.uuid;
	}

	public void setUuid(UUID id) {
		this.uuid = id;
	}

	public String getAuthor() {
		return this.author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLicenseName() {
		return this.licenseName;
	}

	public void setLicenseName(String licenseName) {
		this.licenseName = licenseName;
	}

	public String getLicenseText() {
		return this.licenseText;
	}

	public void setLicenseText(String licenseText) {
		this.licenseText = licenseText;
	}

	public String getMetaVersion() {
		return this.metaVersion;
	}

	public void setMetaVersion(String metaVersion) {
		this.metaVersion = metaVersion;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOrganization() {
		return this.organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public byte[] getPreviewImage() {
		return this.previewImage;
	}

	public void setPreviewImage(byte[] previewImage) {
		this.previewImage = previewImage;
	}

	public String getSbiVersionDe() {
		return this.sbiVersionDe;
	}

	public void setSbiVersionDe(String sbiVersionDe) {
		this.sbiVersionDe = sbiVersionDe;
	}

	public String getSbiVersionIn() {
		return this.sbiVersionIn;
	}

	public void setSbiVersionIn(String sbiVersionIn) {
		this.sbiVersionIn = sbiVersionIn;
	}

	public String getSbiVersionUp() {
		return this.sbiVersionUp;
	}

	public void setSbiVersionUp(String sbiVersionUp) {
		this.sbiVersionUp = sbiVersionUp;
	}

	public String getTemplate() {
		return this.template;
	}

	public void setTemplate(String template) {
		this.template = template;
	}

	public Timestamp getTimeDe() {
		return this.timeDe;
	}

	public void setTimeDe(Timestamp timeDe) {
		this.timeDe = timeDe;
	}

	public Timestamp getTimeIn() {
		return this.timeIn;
	}

	public void setTimeIn(Timestamp timeIn) {
		this.timeIn = timeIn;
	}

	public Timestamp getTimeUp() {
		return this.timeUp;
	}

	public void setTimeUp(Timestamp timeUp) {
		this.timeUp = timeUp;
	}

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getUsageCounter() {
		return this.usageCounter;
	}

	public void setUsageCounter(int usageCounter) {
		this.usageCounter = usageCounter;
	}

	public String getUserDe() {
		return this.userDe;
	}

	public void setUserDe(String userDe) {
		this.userDe = userDe;
	}

	public String getUserIn() {
		return this.userIn;
	}

	public void setUserIn(String userIn) {
		this.userIn = userIn;
	}

	public String getUserUp() {
		return this.userUp;
	}

	public void setUserUp(String userUp) {
		this.userUp = userUp;
	}

	public List<SbiWidgetGalleryTag> getSbiWidgetGalleryTags() {
		return this.sbiWidgetGalleryTags;
	}

	public void setSbiWidgetGalleryTags(List<SbiWidgetGalleryTag> sbiWidgetGalleryTags) {
		this.sbiWidgetGalleryTags = sbiWidgetGalleryTags;
	}

	public SbiWidgetGalleryTag addSbiWidgetGalleryTag(SbiWidgetGalleryTag sbiWidgetGalleryTag) {
		getSbiWidgetGalleryTags().add(sbiWidgetGalleryTag);
		sbiWidgetGalleryTag.setSbiWidgetGallery(this);

		return sbiWidgetGalleryTag;
	}

	public SbiWidgetGalleryTag removeSbiWidgetGalleryTag(SbiWidgetGalleryTag sbiWidgetGalleryTag) {
		getSbiWidgetGalleryTags().remove(sbiWidgetGalleryTag);
		sbiWidgetGalleryTag.setSbiWidgetGallery(null);

		return sbiWidgetGalleryTag;
	}

}