import torch
import torch.nn as nn
from transformers import T5ForConditionalGeneration

class ServerModel(nn.Module):
    def __init__(self, pretrained_model_name='t5-small', split_layer=3):
        super(ServerModel, self).__init__()
        full_model = T5ForConditionalGeneration.from_pretrained(pretrained_model_name)
        # encoder 부분 분리
        self.encoder_tail = nn.Sequential(*list(full_model.encoder.block.children())[split_layer:])
        self.decoder = full_model.decoder
        self.lm_head = full_model.lm_head
        self.config = full_model.config

    def forward(self, smashed_data, attention_mask=None, labels=None):
        encoder_outputs = self.encoder_tail(smashed_data)
        decoder_input_ids = torch.full(
            (smashed_data.size(0), 1),
            self.config.decoder_start_token_id,
            dtype=torch.long,
            device=smashed_data.device
        )
        outputs = self.decoder(input_ids=decoder_input_ids, encoder_hidden_states=encoder_outputs.last_hidden_state)
        logits = self.lm_head(outputs.last_hidden_state)
        return logits
