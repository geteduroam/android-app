package app.eduroam.geteduroam.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.models.Organization


@Composable
fun OrganizationRow(
    organization: Organization,
    configuredOrganization: ConfiguredOrganization?,
    onSelectOrganization: (Organization, ConfiguredOrganization?) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier.clickable { onSelectOrganization(organization, configuredOrganization) },
    color = MaterialTheme.colorScheme.surface
) {
    Column(
        modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier
                    .weight(1f)
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = organization.getLocalizedName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = organization.country,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.width(8.dp))
            if (configuredOrganization != null) {
                Icon(
                    painterResource(R.drawable.ic_saved_organization),
                    contentDescription = "Saved organization indicator",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                painterResource(R.drawable.ic_caret_right),
                contentDescription = "Select organization arrow",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        }
        HorizontalDivider(
            Modifier
                .height(0.5.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}